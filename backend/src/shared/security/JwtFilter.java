package shared.security;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import modules.admin.model.AppUser;
import modules.admin.repository.UserRepo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Reads the "Authorization: Bearer ..." header and signs the user in for this request. */
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwt;
    private final UserRepo users;

    public JwtFilter(JwtService jwt, UserRepo users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String username = jwt.parseUsername(header.substring(7));
            if (username != null) {
                users.findByUsernameIgnoreCase(username)
                        .filter(AppUser::isActive)
                        .ifPresent(this::signIn);
            }
        }
        chain.doFilter(request, response);
    }

    private void signIn(AppUser u) {
        Set<String> perms = Set.copyOf(u.getRole().getPermissions());
        AuthUser principal = new AuthUser(
                u.getId(),
                u.getUsername(),
                u.getEmployee() == null ? null : u.getEmployee().getId(),
                u.getRole().getName(),
                perms);
        List<SimpleGrantedAuthority> authorities = perms.stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }
}
