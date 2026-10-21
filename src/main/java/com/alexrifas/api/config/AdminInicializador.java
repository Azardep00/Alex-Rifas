package com.alexrifas.api.config;

import com.alexrifas.api.entity.Usuario;
import com.alexrifas.api.model.Rol;
import com.alexrifas.api.repository.UsuarioRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the initial administrator only when both environment variables are provided. */
@Component
public class AdminInicializador implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminInicializador.class);
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final String correo;
    private final String contrasena;

    public AdminInicializador(UsuarioRepository usuarios, PasswordEncoder encoder, Clock clock,
            @Value("${ADMIN_CORREO:}") String correo,
            @Value("${ADMIN_CONTRASENA:}") String contrasena) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.clock = clock;
        this.correo = correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
        this.contrasena = contrasena == null ? "" : contrasena;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (correo.isBlank() || contrasena.isBlank()) {
            log.warn("No se creó administrador inicial: define ADMIN_CORREO y ADMIN_CONTRASENA en el entorno.");
            return;
        }
        if (usuarios.existsByRol(Rol.ADMIN)) {
            return;
        }
        if (usuarios.existsByCorreo(correo)) {
            throw new IllegalStateException("ADMIN_CORREO ya pertenece a otro usuario; define un correo libre.");
        }
        if (contrasena.length() < 10 || contrasena.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("ADMIN_CONTRASENA debe tener al menos 10 caracteres y máximo 72 bytes UTF-8.");
        }
        Usuario admin = new Usuario();
        admin.setNombre("Administrador");
        admin.setApellido("Sistema");
        admin.setTelefono("0000000000");
        admin.setCorreo(correo);
        admin.setContrasenaHash(encoder.encode(contrasena));
        admin.setRol(Rol.ADMIN);
        admin.setActivo(true);
        admin.setCreadoEn(Instant.now(clock));
        usuarios.save(admin);
        log.info("Se creó el administrador inicial configurado por entorno.");
    }
}
