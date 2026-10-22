package com.alexrifas.api.dto;

import com.alexrifas.api.model.Rol;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegistroRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 80, message = "El nombre no puede superar 80 caracteres.") String nombre,
        @NotBlank(message = "El apellido es obligatorio.")
        @Size(max = 80, message = "El apellido no puede superar 80 caracteres.") String apellido,
        @NotBlank(message = "El teléfono es obligatorio.")
        @Pattern(regexp = "^[0-9+()\\-\\s]{7,30}$", message = "El teléfono tiene un formato inválido.") String telefono,
        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El correo tiene un formato inválido.")
        @Size(max = 160, message = "El correo no puede superar 160 caracteres.") String correo,
        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(min = 10, max = 72, message = "La contraseña debe tener entre 10 y 72 caracteres.") String contrasena,
        @NotNull(message = "La fecha de nacimiento es obligatoria.")
        @Past(message = "La fecha de nacimiento debe ser una fecha pasada.") LocalDate fechaNacimiento,
        @AssertTrue(message = "Debes aceptar los términos y condiciones.") boolean aceptoTerminos) {}

    public record LoginRequest(
        @NotBlank(message = "El correo es obligatorio.")
        @Size(max = 160, message = "El correo no puede superar 160 caracteres.") String correo,
        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(max = 128, message = "La contraseña es demasiado larga.") String contrasena) {}

    public record RefreshRequest(
        @NotBlank(message = "El refresh token es obligatorio.")
        @Size(max = 200, message = "El refresh token es demasiado largo.") String refreshToken) {}

    public record UsuarioResumen(Long id, String nombre, String apellido, String correo, Rol rol) {}

    public record AuthResponse(String tipo, String accessToken, String refreshToken,
                               long expiraEnSegundos, UsuarioResumen usuario) {}
}
