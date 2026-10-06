
package org.example.service;

import jakarta.annotation.PostConstruct;

import org.apache.xml.security.Init;
import org.apache.xml.security.c14n.Canonicalizer;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.transforms.Transforms;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import javax.security.auth.x500.X500Principal;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;

import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import java.nio.file.Files;
import java.nio.file.Path;

import java.security.KeyStore;
import java.security.PrivateKey;

import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;

import java.security.interfaces.RSAPublicKey;

import java.time.ZoneId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;


@Service
public class XmlSignerService {

    static {

        Init.init();
    }


    private static final String XMLDSIG_NS =
            "http://www.w3.org/2000/09/xmldsig#";


    private static final String SHA256 =
            "http://www.w3.org/2001/04/xmlenc#sha256";


    // =========================================================
    // AMBIENTE
    // =========================================================

    @Value("${sunat.environment:BETA}")
    private String environment;


    // =========================================================
    // RSA
    // =========================================================

    @Value("${sunat.certificate.min-rsa-bits:1024}")
    private int minRsaBits;


    // =========================================================
    // TRUSTSTORE PRODUCCIÓN
    // =========================================================

    @Value("${sunat.certificate.production.truststore-path:}")
    private String trustStorePath;


    @Value("${sunat.certificate.production.truststore-password:}")
    private String trustStorePassword;


    @Value("${sunat.certificate.production.truststore-type:PKCS12}")
    private String trustStoreType;


    private Set<TrustAnchor> trustAnchors =
            Set.of();


    // =========================================================
    // INICIALIZACIÓN
    // =========================================================

    @PostConstruct
    public void inicializar() {

        /*
         * =====================================================
         * BETA
         * =====================================================
         *
         * No necesitamos truststore.
         *
         * Permitimos certificados autofirmados.
         *
         *
         * =====================================================
         * PRODUCCIÓN
         * =====================================================
         *
         * El backend NO arrancará correctamente la validación
         * de certificados productivos sin un truststore.
         *
         * Esto evita aceptar silenciosamente certificados
         * arbitrarios.
         */

        if (esProduccion()) {

            trustAnchors =
                    cargarTrustAnchorsProduccion();
        }
    }


    // =========================================================
    // FIRMAR XML
    // =========================================================

    public ResultadoFirma firmarXml(

            byte[] xmlBytes,

            byte[] pfxBytes,

            String pfxPassword
    ) {

        try {

            if (
                    xmlBytes == null
                            ||
                            xmlBytes.length == 0
            ) {

                throw new IllegalArgumentException(
                        "No existe XML para firmar."
                );
            }


            if (
                    pfxBytes == null
                            ||
                            pfxBytes.length == 0
            ) {

                throw new IllegalArgumentException(
                        "No existe certificado digital PFX."
                );
            }


            if (
                    pfxPassword == null
                            ||
                            pfxPassword.isBlank()
            ) {

                throw new IllegalArgumentException(
                        "No existe contraseña del certificado PFX."
                );
            }


            // =================================================
            // XML SEGURO
            // =================================================

            Document doc =
                    parseXmlSeguro(
                            xmlBytes
                    );


            // =================================================
            // CARGAR PFX
            // =================================================

            DatosCertificado datos =
                    cargarCertificado(
                            pfxBytes,
                            pfxPassword
                    );


            // =================================================
            // PRODUCCIÓN:
            // REVALIDAR JUSTO ANTES DE FIRMAR
            // =================================================
            //
            // Esto es importante porque evita que:
            //
            // 1. configuremos un PFX BETA
            // 2. cambiemos el servidor a PROD
            // 3. ese certificado BETA pueda seguir firmando.
            //
            // =================================================

            if (esProduccion()) {

                String rucXml =
                        extraerRucEmisor(
                                doc
                        );


                validarCertificadoProduccion(

                        datos,

                        rucXml
                );
            }


            // =================================================
            // EXTENSION CONTENT
            // =================================================

            NodeList extensionContents =
                    doc.getElementsByTagNameNS(
                            "*",
                            "ExtensionContent"
                    );


            if (
                    extensionContents.getLength()
                            ==
                            0
            ) {

                throw new IllegalArgumentException(
                        "No se encontró ext:ExtensionContent."
                );
            }


            Element target =
                    (Element)
                            extensionContents
                                    .item(0);


            // =================================================
            // FIRMA RSA SHA-256
            // =================================================

            XMLSignature signature =
                    new XMLSignature(

                            doc,

                            "",

                            XMLSignature
                                    .ALGO_ID_SIGNATURE_RSA_SHA256,

                            Canonicalizer
                                    .ALGO_ID_C14N_EXCL_OMIT_COMMENTS
                    );


            signature
                    .getElement()
                    .setAttribute(
                            "Id",
                            "SignatureSP"
                    );


            target.appendChild(
                    signature.getElement()
            );


            Transforms transforms =
                    new Transforms(
                            doc
                    );


            transforms.addTransform(
                    Transforms
                            .TRANSFORM_ENVELOPED_SIGNATURE
            );


            transforms.addTransform(
                    Transforms
                            .TRANSFORM_C14N_EXCL_OMIT_COMMENTS
            );


            signature.addDocument(

                    "",

                    transforms,

                    SHA256
            );


            signature.addKeyInfo(
                    datos.certificado()
            );


            signature.sign(
                    datos.privateKey()
            );


            // =================================================
            // HASH
            // =================================================

            NodeList digestValues =
                    doc.getElementsByTagNameNS(

                            XMLDSIG_NS,

                            "DigestValue"
                    );


            String hashCpe =
                    digestValues.getLength() > 0

                            ?

                            digestValues
                                    .item(0)
                                    .getTextContent()

                            :

                            "";


            // =================================================
            // SERIALIZAR
            // =================================================

            ByteArrayOutputStream baos =
                    new ByteArrayOutputStream();


            TransformerFactory tf =
                    TransformerFactory
                            .newInstance();


            tf.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_DTD,
                    ""
            );


            tf.setAttribute(
                    XMLConstants.ACCESS_EXTERNAL_STYLESHEET,
                    ""
            );


            Transformer transformer =
                    tf.newTransformer();


            transformer.setOutputProperty(
                    OutputKeys.ENCODING,
                    "UTF-8"
            );


            transformer.setOutputProperty(
                    OutputKeys.OMIT_XML_DECLARATION,
                    "no"
            );


            transformer.transform(

                    new DOMSource(
                            doc
                    ),

                    new StreamResult(
                            baos
                    )
            );


            return new ResultadoFirma(

                    baos.toByteArray(),

                    hashCpe
            );


        } catch (IllegalArgumentException e) {

            throw e;


        } catch (Exception e) {

            throw new RuntimeException(
                    "Error firmando XML: "
                            +
                            e.getMessage(),
                    e
            );
        }
    }


    // =========================================================
    // VALIDAR PFX
    // =========================================================
    //
    // Método mantenido para compatibilidad BETA.
    //
    // En PRODUCCIÓN queremos utilizar siempre la versión
    // que recibe RUC.
    //
    // =========================================================

    public CertificadoInfo validarPfx(

            byte[] pfxBytes,

            String password
    ) {

        DatosCertificado datos =
                cargarCertificado(
                        pfxBytes,
                        password
                );


        if (esProduccion()) {

            throw new IllegalStateException(
                    "En PRODUCCIÓN la validación del certificado requiere el RUC de la empresa."
            );
        }


        return certificadoInfo(
                datos.certificado()
        );
    }


    // =========================================================
    // VALIDAR PFX PARA EMPRESA
    // =========================================================

    public CertificadoInfo validarPfx(

            byte[] pfxBytes,

            String password,

            String rucEsperado
    ) {

        DatosCertificado datos =
                cargarCertificado(
                        pfxBytes,
                        password
                );


        if (esProduccion()) {

            validarCertificadoProduccion(

                    datos,

                    rucEsperado
            );
        }


        return certificadoInfo(
                datos.certificado()
        );
    }


    // =========================================================
    // CARGAR CERTIFICADO
    // =========================================================

    private DatosCertificado cargarCertificado(

            byte[] pfxBytes,

            String password
    ) {

        if (
                pfxBytes == null
                        ||
                        pfxBytes.length == 0
        ) {

            throw new IllegalArgumentException(
                    "El certificado está vacío."
            );
        }


        if (
                password == null
                        ||
                        password.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "La contraseña del certificado es obligatoria."
            );
        }


        char[] passwordChars =
                password.toCharArray();


        try {

            KeyStore keyStore =
                    KeyStore.getInstance(
                            "PKCS12"
                    );


            keyStore.load(

                    new ByteArrayInputStream(
                            pfxBytes
                    ),

                    passwordChars
            );


            Enumeration<String> aliases =
                    keyStore.aliases();


            String aliasValido =
                    null;


            while (
                    aliases.hasMoreElements()
            ) {

                String alias =
                        aliases.nextElement();


                if (
                        keyStore.isKeyEntry(
                                alias
                        )
                ) {

                    aliasValido =
                            alias;

                    break;
                }
            }


            if (
                    aliasValido == null
            ) {

                throw new IllegalArgumentException(
                        "El PFX no contiene una clave privada."
                );
            }


            PrivateKey privateKey =
                    (PrivateKey)
                            keyStore
                                    .getKey(

                                            aliasValido,

                                            passwordChars
                                    );


            X509Certificate certificado =
                    (X509Certificate)
                            keyStore
                                    .getCertificate(
                                            aliasValido
                                    );


            if (
                    privateKey == null
                            ||
                            certificado == null
            ) {

                throw new IllegalArgumentException(
                        "Certificado digital incompleto."
                );
            }


            // =================================================
            // VIGENCIA
            // =================================================

            certificado
                    .checkValidity();


            // =================================================
            // RSA
            // =================================================

            validarRsa(

                    privateKey,

                    certificado
            );


            // =================================================
            // KEY USAGE
            // =================================================

            validarUsoFirma(
                    certificado
            );


            // =================================================
            // CADENA DEL PFX
            // =================================================

            List<X509Certificate> cadena =
                    new ArrayList<>();


            java.security.cert.Certificate[]
                    certificadosCadena =
                    keyStore
                            .getCertificateChain(
                                    aliasValido
                            );


            if (
                    certificadosCadena != null
                            &&
                            certificadosCadena.length > 0
            ) {

                for (
                        java.security.cert.Certificate elemento :
                        certificadosCadena
                ) {

                    if (
                            elemento
                                    instanceof
                                    X509Certificate cert
                    ) {

                        cadena.add(
                                cert
                        );
                    }
                }

            } else {

                cadena.add(
                        certificado
                );
            }


            return new DatosCertificado(

                    privateKey,

                    certificado,

                    List.copyOf(
                            cadena
                    )
            );


        } catch (IllegalArgumentException e) {

            throw e;


        } catch (Exception e) {

            throw new IllegalArgumentException(
                    "Certificado PFX inválido: "
                            +
                            e.getMessage(),
                    e
            );


        } finally {

            Arrays.fill(
                    passwordChars,
                    '\0'
            );
        }
    }


    // =========================================================
    // VALIDACIÓN PRODUCCIÓN
    // =========================================================

    private void validarCertificadoProduccion(

            DatosCertificado datos,

            String rucEsperado
    ) {

        if (
                rucEsperado == null
                        ||
                        !rucEsperado.matches(
                                "^[0-9]{11}$"
                        )
        ) {

            throw new IllegalArgumentException(
                    "No se puede validar el certificado: RUC inválido."
            );
        }


        X509Certificate certificado =
                datos.certificado();


        // =====================================================
        // RUC EN OU
        // =====================================================

        if (
                !certificadoContieneRucEnOu(

                        certificado,

                        rucEsperado
                )
        ) {

            throw new IllegalArgumentException(
                    "El certificado digital no corresponde al RUC de esta empresa."
            );
        }


        // =====================================================
        // AUTOFIRMADO
        // =====================================================

        if (
                esAutofirmado(
                        certificado
                )
        ) {

            throw new IllegalArgumentException(
                    "En PRODUCCIÓN no se permiten certificados autofirmados."
            );
        }


        // =====================================================
        // CERTIFICADO DE CA NO PUEDE SER EL CERTIFICADO EMISOR
        // =====================================================

        if (
                certificado.getBasicConstraints()
                        >=
                        0
        ) {

            throw new IllegalArgumentException(
                    "El certificado seleccionado corresponde a una autoridad certificadora y no a un emisor."
            );
        }


        // =====================================================
        // CADENA CONFIABLE
        // =====================================================

        validarCadenaConfianza(
                datos.cadena()
        );
    }


    // =========================================================
    // RSA
    // =========================================================

    private void validarRsa(

            PrivateKey privateKey,

            X509Certificate certificado
    ) {

        if (
                !"RSA".equalsIgnoreCase(
                        privateKey.getAlgorithm()
                )
        ) {

            throw new IllegalArgumentException(
                    "El certificado debe utilizar una clave privada RSA."
            );
        }


        if (
                !(certificado.getPublicKey()
                        instanceof RSAPublicKey rsaPublicKey)
        ) {

            throw new IllegalArgumentException(
                    "La clave pública del certificado no es RSA."
            );
        }


        int bits =
                rsaPublicKey
                        .getModulus()
                        .bitLength();


        if (
                bits < minRsaBits
        ) {

            throw new IllegalArgumentException(
                    "La clave RSA del certificado es demasiado pequeña. Mínimo permitido: "
                            +
                            minRsaBits
                            +
                            " bits."
            );
        }
    }


    // =========================================================
    // KEY USAGE
    // =========================================================

    private void validarUsoFirma(

            X509Certificate certificado
    ) {

        boolean[] uso =
                certificado.getKeyUsage();


        /*
         * Algunos certificados no incluyen
         * explícitamente KeyUsage.
         *
         * Si existe, aceptamos:
         *
         * digitalSignature
         * o
         * nonRepudiation/contentCommitment
         */

        if (
                uso != null
        ) {

            boolean digitalSignature =
                    uso.length > 0
                            &&
                            uso[0];


            boolean nonRepudiation =
                    uso.length > 1
                            &&
                            uso[1];


            if (
                    !digitalSignature
                            &&
                            !nonRepudiation
            ) {

                throw new IllegalArgumentException(
                        "El certificado no está habilitado para firma digital."
                );
            }
        }
    }


    // =========================================================
    // RUC EN OU
    // =========================================================

    private boolean certificadoContieneRucEnOu(

            X509Certificate certificado,

            String ruc
    ) {

        try {

            LdapName subject =
                    new LdapName(

                            certificado
                                    .getSubjectX500Principal()
                                    .getName(
                                            X500Principal.RFC2253
                                    )
                    );


            for (
                    Rdn rdn :
                    subject.getRdns()
            ) {

                if (
                        "OU".equalsIgnoreCase(
                                rdn.getType()
                        )
                ) {

                    String valor =
                            String
                                    .valueOf(
                                            rdn.getValue()
                                    )
                                    .trim();


                    if (
                            ruc.equals(
                                    valor
                            )
                    ) {

                        return true;
                    }
                }
            }


            return false;


        } catch (Exception e) {

            throw new IllegalArgumentException(
                    "No se pudo leer la identidad del certificado.",
                    e
            );
        }
    }


    // =========================================================
    // AUTOFIRMADO
    // =========================================================

    private boolean esAutofirmado(

            X509Certificate certificado
    ) {

        if (
                !certificado
                        .getSubjectX500Principal()
                        .equals(
                                certificado
                                        .getIssuerX500Principal()
                        )
        ) {

            return false;
        }


        try {

            certificado.verify(
                    certificado.getPublicKey()
            );


            return true;


        } catch (Exception e) {

            return false;
        }
    }


    // =========================================================
    // TRUSTSTORE PRODUCCIÓN
    // =========================================================

    private Set<TrustAnchor>
    cargarTrustAnchorsProduccion() {

        if (
                trustStorePath == null
                        ||
                        trustStorePath.isBlank()
        ) {

            throw new IllegalStateException(
                    "PRODUCCIÓN requiere SUNAT_CERT_TRUSTSTORE_PATH."
            );
        }


        if (
                trustStorePassword == null
                        ||
                        trustStorePassword.isBlank()
        ) {

            throw new IllegalStateException(
                    "PRODUCCIÓN requiere SUNAT_CERT_TRUSTSTORE_PASSWORD."
            );
        }


        char[] password =
                trustStorePassword
                        .toCharArray();


        try {

            KeyStore trustStore =
                    KeyStore.getInstance(
                            trustStoreType
                    );


            try (
                    InputStream input =
                            Files.newInputStream(
                                    Path.of(
                                            trustStorePath
                                    )
                            )
            ) {

                trustStore.load(
                        input,
                        password
                );
            }


            Set<TrustAnchor> anchors =
                    new HashSet<>();


            Enumeration<String> aliases =
                    trustStore.aliases();


            while (
                    aliases.hasMoreElements()
            ) {

                String alias =
                        aliases.nextElement();


                java.security.cert.Certificate certificado =
                        trustStore
                                .getCertificate(
                                        alias
                                );


                if (
                        certificado
                                instanceof
                                X509Certificate x509
                ) {

                    anchors.add(
                            new TrustAnchor(
                                    x509,
                                    null
                            )
                    );
                }
            }


            if (
                    anchors.isEmpty()
            ) {

                throw new IllegalStateException(
                        "El truststore productivo no contiene certificados de confianza."
                );
            }


            return Set.copyOf(
                    anchors
            );


        } catch (Exception e) {

            throw new IllegalStateException(
                    "No se pudo cargar el truststore productivo: "
                            +
                            e.getMessage(),
                    e
            );


        } finally {

            Arrays.fill(
                    password,
                    '\0'
            );
        }
    }


    // =========================================================
    // VALIDAR CADENA
    // =========================================================

    private void validarCadenaConfianza(

            List<X509Certificate> cadena
    ) {

        try {

            if (
                    trustAnchors == null
                            ||
                            trustAnchors.isEmpty()
            ) {

                throw new IllegalStateException(
                        "No existe truststore productivo configurado."
                );
            }


            List<X509Certificate> ruta =
                    new ArrayList<>(
                            cadena
                    );


            /*
             * Si el último elemento ya es un TrustAnchor,
             * no debe formar parte del CertPath.
             */

            if (
                    !ruta.isEmpty()
                            &&
                            esTrustAnchor(
                                    ruta.get(
                                            ruta.size() - 1
                                    )
                            )
            ) {

                ruta.remove(
                        ruta.size() - 1
                );
            }


            if (
                    ruta.isEmpty()
            ) {

                throw new IllegalArgumentException(
                        "La cadena del certificado es inválida."
                );
            }


            CertificateFactory factory =
                    CertificateFactory
                            .getInstance(
                                    "X.509"
                            );


            CertPath certPath =
                    factory
                            .generateCertPath(
                                    ruta
                            );


            PKIXParameters parameters =
                    new PKIXParameters(
                            trustAnchors
                    );


            /*
             * IMPORTANTE:
             *
             * Esta versión valida la cadena de confianza.
             *
             * Todavía NO estamos haciendo comprobación
             * OCSP/CRL de revocación.
             *
             * Eso será otro endurecimiento antes
             * del deploy final si decidimos manejarlo
             * localmente.
             */

            parameters.setRevocationEnabled(
                    false
            );


            CertPathValidator
                    .getInstance(
                            "PKIX"
                    )
                    .validate(

                            certPath,

                            parameters
                    );


        } catch (IllegalArgumentException e) {

            throw e;


        } catch (Exception e) {

            throw new IllegalArgumentException(
                    "La cadena de confianza del certificado no es válida: "
                            +
                            e.getMessage(),
                    e
            );
        }
    }


    // =========================================================
    // ¿ES TRUST ANCHOR?
    // =========================================================

    private boolean esTrustAnchor(

            X509Certificate certificado
    ) {

        try {

            byte[] encoded =
                    certificado
                            .getEncoded();


            for (
                    TrustAnchor anchor :
                    trustAnchors
            ) {

                X509Certificate trusted =
                        anchor.getTrustedCert();


                if (
                        trusted != null
                                &&
                                Arrays.equals(

                                        encoded,

                                        trusted.getEncoded()
                                )
                ) {

                    return true;
                }
            }


            return false;


        } catch (Exception e) {

            return false;
        }
    }


    // =========================================================
    // RUC DESDE XML
    // =========================================================
    //
    // En PRODUCCIÓN volvemos a verificar justo antes
    // de firmar que el certificado corresponda al RUC
    // del comprobante.
    //
    // =========================================================

    private String extraerRucEmisor(

            Document doc
    ) {

        NodeList proveedores =
                doc.getElementsByTagNameNS(

                        "*",

                        "AccountingSupplierParty"
                );


        if (
                proveedores.getLength()
                        ==
                        0
        ) {

            throw new IllegalArgumentException(
                    "No se encontró AccountingSupplierParty en el XML."
            );
        }


        Element proveedor =
                (Element)
                        proveedores
                                .item(0);


        /*
         * =====================================================
         * UBL 2.0 - RESUMEN DIARIO
         * =====================================================
         *
         * SummaryDocuments utiliza:
         *
         * cac:AccountingSupplierParty
         *   /cbc:CustomerAssignedAccountID
         *
         * para el RUC del emisor.
         */
        String ruc =
                primerRucEnElementos(
                        proveedor,
                        "CustomerAssignedAccountID"
                );


        if (ruc != null) {
            return ruc;
        }


        /*
         * =====================================================
         * UBL 2.1 - FACTURA / BOLETA / NOTA
         * =====================================================
         *
         * Las estructuras UBL 2.1 actuales del proyecto contienen
         * el RUC en un cbc:ID dentro del proveedor.
         */
        ruc =
                primerRucEnElementos(
                        proveedor,
                        "ID"
                );


        if (ruc != null) {
            return ruc;
        }


        throw new IllegalArgumentException(
                "No se encontró el RUC emisor dentro del XML."
        );
    }


    /**
     * Busca el primer valor de 11 dígitos dentro de los elementos
     * indicados que pertenecen al AccountingSupplierParty.
     */
    private String primerRucEnElementos(

            Element proveedor,

            String localName
    ) {

        NodeList nodes =
                proveedor
                        .getElementsByTagNameNS(
                                "*",
                                localName
                        );


        for (
                int i = 0;
                i < nodes.getLength();
                i++
        ) {

            String valor =
                    nodes
                            .item(i)
                            .getTextContent()
                            .trim();


            if (
                    valor.matches(
                            "^[0-9]{11}$"
                    )
            ) {

                return valor;
            }
        }


        return null;
    }


    // =========================================================
    // XML SEGURO
    // =========================================================

    private Document parseXmlSeguro(

            byte[] xml
    ) throws Exception {

        DocumentBuilderFactory dbf =
                DocumentBuilderFactory
                        .newInstance();


        dbf.setNamespaceAware(
                true
        );


        dbf.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true
        );


        dbf.setFeature(
                "http://xml.org/sax/features/external-general-entities",
                false
        );


        dbf.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false
        );


        dbf.setXIncludeAware(
                false
        );


        dbf.setExpandEntityReferences(
                false
        );


        dbf.setAttribute(
                XMLConstants.ACCESS_EXTERNAL_DTD,
                ""
        );


        dbf.setAttribute(
                XMLConstants.ACCESS_EXTERNAL_SCHEMA,
                ""
        );


        return dbf
                .newDocumentBuilder()
                .parse(
                        new ByteArrayInputStream(
                                xml
                        )
                );
    }


    // =========================================================
    // INFO
    // =========================================================

    private CertificadoInfo certificadoInfo(

            X509Certificate certificado
    ) {

        return new CertificadoInfo(

                certificado
                        .getSubjectX500Principal()
                        .getName(),

                certificado
                        .getIssuerX500Principal()
                        .getName(),

                certificado
                        .getNotBefore()
                        .toInstant()
                        .atZone(
                                ZoneId.of(
                                        "America/Lima"
                                )
                        )
                        .toLocalDate(),

                certificado
                        .getNotAfter()
                        .toInstant()
                        .atZone(
                                ZoneId.of(
                                        "America/Lima"
                                )
                        )
                        .toLocalDate()
        );
    }


    // =========================================================
    // ENVIRONMENT
    // =========================================================

    private boolean esProduccion() {

        String valor =
                environment == null

                        ?

                        ""

                        :

                        environment
                                .trim()
                                .toUpperCase(
                                        Locale.ROOT
                                );


        return valor.equals(
                "PRODUCCION"
        )
                ||
                valor.equals(
                        "PRODUCTION"
                )
                ||
                valor.equals(
                        "PROD"
                );
    }


    // =========================================================
    // RECORDS
    // =========================================================

    private record DatosCertificado(

            PrivateKey privateKey,

            X509Certificate certificado,

            List<X509Certificate> cadena
    ) {
    }


    public record ResultadoFirma(

            byte[] xmlFirmado,

            String hashCpe
    ) {
    }


    public record CertificadoInfo(

            String titular,

            String emisor,

            java.time.LocalDate vigenteDesde,

            java.time.LocalDate vigenteHasta
    ) {
    }
}