package com.alexrifas.api.dto;

import java.time.LocalDate;

import com.alexrifas.api.entity.Usuario;
import com.alexrifas.api.model.Rol;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

/** Solicitudes y respuestas de autenticación. Los DTO evitan exponer o aceptar campos internos (rol, hash...). */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegistroRequest(
            @NotBlank(message = "El nombre es obligatorio.") @Size(max = 80) String nombre,
            @NotBlank(message = "El apellido es obligatorio.") @Size(max = 80) String apellido,
            @NotBlank(message = "El teléfono es obligatorio.") @Size(max = 30) String telefono,
            @NotBlank(message = "El correo es obligatorio.")
            @Email(message = "El correo no tiene un formato válido.") @Size(max = 160) String correo,
            @NotBlank(message = "La contraseña es obligatoria.") @Size(min = 10, max = 72, message = "La contraseña debe tener entre 10 y 72 caracteres.") String contrasena,
            @NotNull(message = "La fecha de nacimiento es obligatoria.")
            @Past(message = "La fecha de nacimiento debe ser pasada.") LocalDate fechaNacimiento,
            @AssertTrue(message = "Debes aceptar los términos y condiciones.") boolean aceptoTerminos) {
    }

    public record CrearOrganizadorRequest(
            @NotBlank(message = "El nombre es obligatorio.") @Size(max = 80) String nombre,
            @NotBlank(message = "El apellido es obligatorio.") @Size(max = 80) String apellido,
            @NotBlank(message = "El teléfono es obligatorio.") @Size(max = 30) String telefono,
            @NotBlank(message = "El correo es obligatorio.")
            @Email(message = "El correo no tiene un formato válido.") @Size(max = 160) String correo,
            @NotBlank(message = "La contraseña es obligatoria.") @Size(min = 10, max = 72, message = "La contraseña debe tener entre 10 y 72 caracteres.") String contrasena) {
    }

    public record LoginRequest(
            @NotBlank(message = "El correo es obligatorio.") @Size(max = 160) String correo,
            @NotBlank(message = "La contraseña es obligatoria.") @Size(min = 10, max = 72, message = "La contraseña debe tener entre 10 y 72 caracteres.") String contrasena) {
    }

    public record RefreshRequest(
            @NotBlank(message = "El refresh token es obligatorio.") @Size(max = 200) String refreshToken) {
    }

    public record UsuarioDto(Long id, String nombre, String apellido, String correo, Rol rol) {

        public static UsuarioDto de(Usuario u) {
            return new UsuarioDto(u.getId(), u.getNombre(), u.getApellido(), u.getCorreo(), u.getRol());
        }
    }

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String tipo,
            long expiraEnSegundos,
            UsuarioDto usuario) {
    }
}
