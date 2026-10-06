package org.example.auth.repository;




import org.example.auth.model.RolUsuario;
import org.example.auth.model.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository
        extends JpaRepository<Usuario, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE LOWER(u.email)=LOWER(:email)")
    Optional<Usuario> bloquearPorEmail(@Param("email") String email);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.id=:id")
    Optional<Usuario> bloquearPorId(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.id=:id AND u.empresa.id=:empresaId")
    Optional<Usuario> bloquearPorIdYEmpresa(@Param("id") Long id, @Param("empresaId") Long empresaId);

    @EntityGraph(
            attributePaths = {
                    "empresa",
                    "permisos"
            }
    )
    Optional<Usuario> findByEmailIgnoreCase(
            String email
    );


    @EntityGraph(
            attributePaths = {
                    "empresa",
                    "permisos"
            }
    )
    @Query("""
            SELECT u
            FROM Usuario u
            WHERE u.id = :id
            """)
    Optional<Usuario> findForSecurityById(
            @Param("id") Long id
    );

    @EntityGraph(
            attributePaths = {
                    "empresa",
                    "permisos"
            }
    )
    List<Usuario>
    findAllByEmpresaIdOrderByNombreAsc(
            Long empresaId
    );

    Optional<Usuario>
    findByIdAndEmpresaId(
            Long id,
            Long empresaId
    );

    boolean existsByEmailIgnoreCase(
            String email
    );

    boolean existsByRol(
            RolUsuario rol
    );

    boolean existsByEmpresaIdAndRol(
            Long empresaId,
            RolUsuario rol
    );
    long countByEmpresaId(
            Long empresaId
    );
}
