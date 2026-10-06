package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.*;
import org.example.auth.service.AuditoriaService;
import org.example.model.*;
import org.example.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.function.Consumer;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.*;

/**
 * Flujo RA para Factura 01 / NC 07 de factura que fueron informadas pero NO otorgadas.
 * Si el documento sí fue otorgado, debe corregirse con Nota de Crédito según corresponda.
 */
@Service @RequiredArgsConstructor @Slf4j
public class ComunicacionBajaService {
    private static final DateTimeFormatter YYYYMMDD=DateTimeFormatter.ofPattern("yyyyMMdd");
    private final ComunicacionBajaRepository repository;
    private final ComprobanteRepository comprobanteRepository;
    private final EmpresaRepository empresaRepository;
    private final SecuenciaComunicacionBajaService secuenciaService;
    private final ComunicacionBajaXmlService xmlService;
    private final XmlSignerService xmlSignerService;
    private final UblValidatorService ublValidatorService;
    private final SunatDirectSoapClient sunatClient;
    private final CdrParserService cdrParserService;
    private final CryptoService cryptoService;
    private final SunatPlazoService plazoService;
    private final AuditoriaService auditoriaService;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;
    private final RespuestaSunatRecibidaService respuestaSunatRecibidaService;

    public ComunicacionBajaResponse crearYEnviar(String ruc, ComunicacionBajaRequest request){
        validarRuc(ruc);
        if(request==null || request.comprobanteId()==null) throw new IllegalArgumentException("Comprobante requerido.");
        Comprobante c=comprobanteRepository.findByIdAndRucEmisor(request.comprobanteId(),ruc)
                .orElseThrow(()->new IllegalArgumentException("Comprobante no encontrado para la empresa autenticada."));
        validarDocumento(c);
        plazoService.validarBaja(c.getFechaEmision().toLocalDate());
        if(repository.existsByDetalles_ComprobanteId(c.getId())) throw new IllegalStateException("El comprobante ya tiene una Comunicación de Baja registrada.");
        LocalDate hoy=LocalDate.now(clock);
        int correlativo=secuenciaService.siguiente(ruc,hoy);
        String id="RA-"+YYYYMMDD.format(hoy)+"-"+correlativo;
        ComunicacionBaja baja=ComunicacionBaja.builder()
                .rucEmisor(ruc).fechaDocumentos(c.getFechaEmision().toLocalDate()).fechaGeneracion(hoy)
                .correlativo(correlativo).identificador(id).nombreArchivoXml(ruc+"-"+id+".xml")
                .estado(EstadoComunicacionBaja.CREADO).build();
        baja.addDetalle(ComunicacionBajaDetalle.builder().comprobanteId(c.getId()).numeroLinea(1)
                .tipoComprobante(c.getTipoComprobante()).serie(c.getSerie()).numero(c.getNumero())
                .motivo(request.motivo().trim()).build());
        baja=repository.saveAndFlush(baja);
        Long idBaja=baja.getId();
        org.example.util.ObservabilidadFiscal.auditar(auditoriaService,ruc,"COMUNICACION_BAJA_CREADA","OK",id+" / comprobanteId="+c.getId());
        prepararYEnviar(ruc,idBaja);
        return obtener(ruc,idBaja);
    }

    public void prepararYEnviar(String ruc,Long bajaId){
        ComunicacionBaja baja=repository.buscarCompleto(bajaId,ruc).orElseThrow(()->new IllegalArgumentException("Comunicación de Baja no encontrada."));
        if(!preparable(baja)) return;
        XmlSignerService.ResultadoFirma firma;
        String usuarioSol,claveSol;
        byte[] zip;
        try{
            plazoService.validarBaja(baja.getFechaDocumentos());
            Empresa empresa=empresaLista(ruc);
            byte[] raw=xmlService.construir(baja,empresa);
            byte[] pfx=cryptoService.descifrarPfx(ruc,empresa.getPfxBytes());
            try{ firma=xmlSignerService.firmarXml(raw,pfx,cryptoService.descifrarPfxPassword(ruc,empresa.getPfxPassword())); }
            finally{ if(pfx!=null) Arrays.fill(pfx,(byte)0); }
            ublValidatorService.validarComunicacionBaja(firma.xmlFirmado());
            usuarioSol=cryptoService.descifrarUsuarioSol(ruc,empresa.getUsuarioSol());
            claveSol=cryptoService.descifrarClaveSol(ruc,empresa.getClaveSol());
            zip=comprimir(baja.getNombreArchivoXml(),firma.xmlFirmado());
        }catch(SunatPlazoService.FueraDePlazoSunatException e){
            actualizarError(ruc,bajaId,false,EstadoComunicacionBaja.FUERA_DE_PLAZO,mensaje(e)); return;
        }catch(Exception e){
            actualizarError(ruc,bajaId,false,EstadoComunicacionBaja.ERROR_ENVIO,"No se pudo preparar RA: "+mensaje(e)); return;
        }
        Boolean tomado=new TransactionTemplate(transactionManager).execute(status->{
            ComunicacionBaja actual=repository.bloquearPorIdYRuc(bajaId,ruc).orElseThrow();
            if(!preparable(actual)) return false;
            actual.setXmlFirmadoBytes(firma.xmlFirmado()); actual.setHashCpe(firma.hashCpe());
            actual.setEstado(EstadoComunicacionBaja.ENVIANDO); actual.setIntentosEnvio(actual.getIntentosEnvio()+1);
            repository.saveAndFlush(actual); return true;
        });
        if(!Boolean.TRUE.equals(tomado)) return;
        try{
            String nombreBase=baja.getNombreArchivoXml().replaceFirst("(?i)\\.xml$","");
            String ticket=sunatClient.enviarResumenDirecto(ruc,usuarioSol,claveSol,nombreBase+".zip",zip);
            new TransactionTemplate(transactionManager).executeWithoutResult(status->{
                empresaRepository.bloquearPorRuc(ruc).orElseThrow();
                ComunicacionBaja actual=repository.bloquearPorIdYRuc(bajaId,ruc).orElseThrow();
                if(actual.getEstado()!=EstadoComunicacionBaja.ENVIANDO && actual.getEstado()!=EstadoComunicacionBaja.ERROR_ENVIO_INCIERTO) return;
                if(actual.getTicketSunat()!=null || !repository.buscarOtrosConTicketActual(ruc,ticket,bajaId).isEmpty())
                    throw new IllegalStateException("Asociación de ticket RA conflictiva; requiere conciliación, no reenvío.");
                actual.setTicketSunat(ticket); actual.setEstado(EstadoComunicacionBaja.TICKET_GENERADO); actual.setEnviadoEn(LocalDateTime.now(clock));
                actual.setMensajeSunat("SUNAT recibió la Comunicación de Baja. Ticket: "+ticket); repository.saveAndFlush(actual);
            });
        }catch(SunatDirectSoapClient.SunatSoapFaultException e){
            actualizarError(ruc,bajaId,true,EstadoComunicacionBaja.ERROR_ENVIO_INCIERTO,mensaje(e)); return;
        }catch(Exception e){
            // Después de adquirir el envío cualquier resultado no concluyente queda bloqueado.
            actualizarError(ruc,bajaId,true,EstadoComunicacionBaja.ERROR_ENVIO_INCIERTO,mensaje(e)); return;
        }
        // Un fallo de consulta/persistencia posterior no es un fallo reenviable de sendSummary.
        consultarTicket(ruc,bajaId);
    }

    private boolean preparable(ComunicacionBaja baja){
        return baja.getTicketSunat()==null && baja.getIntentosEnvio()==0
                && (baja.getEstado()==EstadoComunicacionBaja.CREADO || baja.getEstado()==EstadoComunicacionBaja.ERROR_ENVIO);
    }
    public ComunicacionBajaResponse reintentarEnvio(String ruc,Long id){
        new TransactionTemplate(transactionManager).executeWithoutResult(status->{
            ComunicacionBaja actual=repository.bloquearPorIdYRuc(id,ruc).orElseThrow();
            if(actual.getEstado()!=EstadoComunicacionBaja.ERROR_ENVIO || !preparable(actual))
                throw new IllegalStateException("Sólo se recupera RA con fallo local sin ningún intento de envío registrado.");
        });
        prepararYEnviar(ruc,id); return obtener(ruc,id);
    }
    private void actualizarError(String ruc,Long id,boolean envioAdquirido,EstadoComunicacionBaja estado,String mensaje){
        var actualizado=new TransactionTemplate(transactionManager).execute(status->{
            ComunicacionBaja actual=repository.bloquearPorIdYRuc(id,ruc).orElseThrow();
            if(envioAdquirido ? actual.getEstado()!=EstadoComunicacionBaja.ENVIANDO : !preparable(actual)) return null;
            actual.setEstado(estado); actual.setMensajeSunat(mensaje); repository.save(actual);
            return actual;
        });
        if(actualizado!=null)org.example.util.ObservabilidadFiscal.evento(ruc,id,actualizado.getIdentificador(),actualizado.getTicketSunat(),estado.name(),"FALLO_RA");
    }

    public ComunicacionBajaResponse consultarSunat(String ruc,Long id){ consultarTicket(ruc,id); return obtener(ruc,id); }

    public void consultarTicket(String ruc,Long id){
        ComunicacionBaja baja=repository.buscarCompleto(id,ruc).orElseThrow(()->new IllegalArgumentException("Comunicación de Baja no encontrada."));
        if(baja.getTicketSunat()==null || baja.getTicketSunat().isBlank()) return;
        if(baja.getEstado()==EstadoComunicacionBaja.ACEPTADO || baja.getEstado()==EstadoComunicacionBaja.RECHAZADO) return;
        String ticket=baja.getTicketSunat();
        if(repository.existsByRucEmisorAndTicketSunatAndIdNot(ruc,ticket,id))
            throw new IllegalStateException("Ticket RA ambiguo dentro de la empresa; requiere conciliación.");
        Empresa empresa=empresaLista(ruc);
        try{
            var estado=sunatClient.consultarTicket(ruc,cryptoService.descifrarUsuarioSol(ruc,empresa.getUsuarioSol()),
                    cryptoService.descifrarClaveSol(ruc,empresa.getClaveSol()),ticket);
            respuestaSunatRecibidaService.conservar(ruc,RespuestaSunatRecibida.Flujo.RA,id,ticket,estado.contentZip());
            if(estado.enProceso()){
                actualizarConsulta(ruc,id,ticket,actual->{ actual.setEstado(EstadoComunicacionBaja.EN_PROCESO); actual.setCodigoRespuestaSunat("98"); actual.setMensajeSunat("SUNAT continúa procesando la Comunicación de Baja."); });
                return;
            }
            byte[] zip=estado.contentZip();
            if(zip==null || zip.length==0){
                actualizarConsulta(ruc,id,ticket,actual->{ actual.setEstado(EstadoComunicacionBaja.ERROR_CONSULTA); actual.setCodigoRespuestaSunat(estado.statusCode()); actual.setMensajeSunat("SUNAT finalizó el ticket sin CDR utilizable."); });
                return;
            }
            var cdr=cdrParserService.procesar(zip);
            Boolean finalizado=new TransactionTemplate(transactionManager).execute(status->{
                ComunicacionBaja actual=repository.bloquearPorIdYRuc(id,ruc).orElseThrow();
                if(!consultable(actual) || !ticketCorresponde(actual,ticket)) return false;
                if(cdr.aceptado() && cdr.referenceId()!=null && !cdr.referenceId().isBlank()
                        && !normalizar(cdr.referenceId()).equals(normalizar(actual.getIdentificador())))
                    throw new IllegalStateException("La CDR no corresponde a la Comunicación de Baja esperada.");
                // Mantener la correspondencia existente; no inferir nuevos formatos RC/RA.
                if(cdr.aceptado()){
                    comprobanteRepository.bloquearOriginalesDeNotas(ruc,actual.getDetalles().stream()
                            .map(ComunicacionBajaDetalle::getComprobanteId).toList());
                    for(var d:actual.getDetalles()){
                        Comprobante c=comprobanteRepository.findByIdAndRucEmisor(d.getComprobanteId(),ruc)
                                .orElseThrow(()->new IllegalStateException("Documento de la Comunicación de Baja no encontrado para la empresa."));
                        c.setEstadoSfs(EstadoComprobante.ANULADO); c.setCodigoRespuestaSunat(cdr.responseCode());
                        c.setMensajeSunat(org.example.util.DiagnosticoSunat.acotar("Numeración dada de baja mediante "+actual.getIdentificador()+". "+cdr.description()));
                        comprobanteRepository.save(c);
                    }
                }
                actual.setCdrZipBytes(zip); actual.setCdrXmlBytes(cdr.xml()); actual.setCodigoRespuestaSunat(cdr.responseCode());
                actual.setMensajeSunat(org.example.util.DiagnosticoSunat.acotar(cdr.description())); actual.setFinalizadoEn(LocalDateTime.now(clock));
                actual.setEstado(cdr.aceptado()?EstadoComunicacionBaja.ACEPTADO:EstadoComunicacionBaja.RECHAZADO);
                registrarConsulta(actual);
                repository.saveAndFlush(actual);
                return true;
            });
            if(Boolean.TRUE.equals(finalizado)){
                // registrarSistema usa REQUIRES_NEW: sólo afirmar aceptación después del commit fiscal.
                org.example.util.ObservabilidadFiscal.auditar(auditoriaService,ruc,cdr.aceptado()?"COMUNICACION_BAJA_ACEPTADA":"COMUNICACION_BAJA_RECHAZADA",
                        cdr.aceptado()?"OK":"ERROR",baja.getIdentificador()+" / "+cdr.responseCode());
                org.example.util.ObservabilidadFiscal.evento(ruc,id,baja.getIdentificador(),baja.getTicketSunat(),
                        cdr.aceptado()?"ACEPTADO":"RECHAZADO",cdr.responseCode());
            }
        }catch(SunatDirectSoapClient.SunatTransportException|SunatDirectSoapClient.SunatProtocolException e){
            actualizarConsulta(ruc,id,ticket,actual->{ actual.setEstado(EstadoComunicacionBaja.ERROR_CONSULTA); actual.setMensajeSunat(e.getMessage()); });
        }
    }

    private boolean consultable(ComunicacionBaja baja){
        return baja.getEstado()==EstadoComunicacionBaja.TICKET_GENERADO
                || baja.getEstado()==EstadoComunicacionBaja.EN_PROCESO || baja.getEstado()==EstadoComunicacionBaja.ERROR_CONSULTA;
    }
    private void registrarConsulta(ComunicacionBaja baja){
        baja.setIntentosConsulta(baja.getIntentosConsulta()+1); baja.setUltimaConsultaEn(LocalDateTime.now(clock));
    }
    private boolean ticketCorresponde(ComunicacionBaja actual,String ticket){
        return ticket.equals(actual.getTicketSunat()) && repository.buscarOtrosConTicketActual(actual.getRucEmisor(),ticket,actual.getId()).isEmpty();
    }
    private void actualizarConsulta(String ruc,Long id,String ticket,Consumer<ComunicacionBaja> cambio){
        new TransactionTemplate(transactionManager).executeWithoutResult(status->{
            ComunicacionBaja actual=repository.bloquearPorIdYRuc(id,ruc).orElseThrow();
            if(!consultable(actual) || !ticketCorresponde(actual,ticket)) return;
            cambio.accept(actual); registrarConsulta(actual); repository.save(actual);
        });
    }

    @Transactional(readOnly=true)
    public ComunicacionBajaResponse obtener(String ruc,Long id){ return convertir(repository.findByIdAndRucEmisor(id,ruc).orElseThrow(()->new IllegalArgumentException("Comunicación de Baja no encontrada."))); }
    @Transactional(readOnly=true)
    public List<ComunicacionBajaResponse> listar(String ruc){ return repository.findByRucEmisorOrderByIdDesc(ruc).stream().map(this::convertir).toList(); }

    private void validarDocumento(Comprobante c){
        boolean factura="01".equals(c.getTipoComprobante());
        boolean ncFactura="07".equals(c.getTipoComprobante()) && "01".equals(c.getTipoComprobanteAfectado());
        if(!factura && !ncFactura) throw new IllegalArgumentException("RA sólo aplica aquí a Factura o NC vinculada a Factura. La boleta usa Resumen Diario con estado 3.");
        if(!(c.getEstadoSfs()==EstadoComprobante.ACEPTADO || c.getEstadoSfs()==EstadoComprobante.ACEPTADO_CON_OBSERVACION))
            throw new IllegalStateException("El comprobante debe estar informado/aceptado antes de comunicar su baja.");
    }
    private Empresa empresaLista(String ruc){
        Empresa e=empresaRepository.findByRuc(ruc).orElseThrow(()->new IllegalArgumentException("Empresa emisora no encontrada."));
        if(!e.tieneConfiguracionSunat()) throw new IllegalArgumentException("La empresa no tiene configuración SUNAT completa.");
        if(e.getCertificadoVigenteHasta()!=null && e.getCertificadoVigenteHasta().isBefore(LocalDate.now(clock))) throw new IllegalArgumentException("El certificado digital está vencido.");
        return e;
    }
    private ComunicacionBajaResponse convertir(ComunicacionBaja b){ return new ComunicacionBajaResponse(b.getId(),b.getIdentificador(),b.getNombreArchivoXml(),b.getFechaDocumentos(),b.getFechaGeneracion(),b.getEstado().name(),b.getTicketSunat(),b.getCodigoRespuestaSunat(),b.getMensajeSunat(),b.getEnviadoEn(),b.getUltimaConsultaEn(),b.getFinalizadoEn(),b.getCreatedAt(),b.getUpdatedAt()); }
    private byte[] comprimir(String nombre,byte[] xml)throws IOException{ ByteArrayOutputStream out=new ByteArrayOutputStream(); try(ZipOutputStream z=new ZipOutputStream(out)){ z.putNextEntry(new ZipEntry(nombre)); z.write(xml); z.closeEntry(); } return out.toByteArray(); }
    private void validarRuc(String ruc){ if(ruc==null || !ruc.matches("^[0-9]{11}$")) throw new IllegalArgumentException("RUC inválido."); }
    private String normalizar(String x){ return x==null?"":x.trim().toUpperCase(Locale.ROOT).replaceFirst("^R-",""); }
    private String mensaje(Exception e){ return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(); }
}
