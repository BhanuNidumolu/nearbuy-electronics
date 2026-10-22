package dev.nearbuy;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){ return new BCryptPasswordEncoder(); }
 @Bean SecretKey jwtKey(@Value("${app.jwt-secret}") String secret) {
  if(secret.getBytes(StandardCharsets.UTF_8).length<32) throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
  return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
 }
 @Bean JwtFilter jwtFilter(SecretKey key){ return new JwtFilter(key); }
 @Bean SecurityFilterChain filterChain(HttpSecurity http, JwtFilter jwt) throws Exception {
  return http.csrf(c->c.disable()).cors(c->{}).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
   .authorizeHttpRequests(a->a.requestMatchers("/api/auth/**","/api/offers/**","/api/health","/swagger-ui/**","/swagger-ui.html","/v3/api-docs/**","/","/index.html","/assets/**","/favicon.ico").permitAll().anyRequest().authenticated())
   .addFilterBefore(jwt, UsernamePasswordAuthenticationFilter.class).build();
 }
}
class JwtFilter extends OncePerRequestFilter {
 private final SecretKey key;
 JwtFilter(SecretKey key){this.key=key;}
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
  String h=req.getHeader(HttpHeaders.AUTHORIZATION);
  if(h!=null && h.startsWith("Bearer ")) try {
   Claims c=Jwts.parser().verifyWith(key).build().parseSignedClaims(h.substring(7)).getPayload();
   String email=c.getSubject(), role=c.get("role",String.class), id=c.get("uid",String.class);
   var auth=new UsernamePasswordAuthenticationToken(email,id,List.of(new SimpleGrantedAuthority("ROLE_"+role)));
   req.setAttribute("userId",Long.valueOf(id));
   org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
  } catch(Exception ignored) { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
  chain.doFilter(req,res);
 }
}
