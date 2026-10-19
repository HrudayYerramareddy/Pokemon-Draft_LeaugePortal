package com.pokemondraft.leagueportal.services;

import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.AppUserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
  public static final String SESSION_USER_ID = "userId";
  private final AppUserRepository users;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  public AuthService(AppUserRepository users) {
    this.users = users;
  }

  @Transactional
  public AppUser login(String username, String password, HttpSession session) {
    if (username == null || username.isBlank())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter your username.");
    if (password == null || password.isEmpty())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter your password.");
    AppUser user =
        users
            .findByUsername(username.trim())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Username not found. Check the spelling and try again."));
    if (!matches(password, user.getPassword()))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
          "Incorrect password. Try again or ask the commissioner for a password reset.");
    if (!isHashed(user.getPassword())) {
      user.setPassword(encoder.encode(password));
      users.save(user);
    }
    session.setAttribute(SESSION_USER_ID, user.getId());
    return user;
  }

  private boolean isHashed(String value) {
    return value != null && value.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");
  }

  private boolean matches(String supplied, String stored) {
    return stored != null && (isHashed(stored) ? encoder.matches(supplied, stored) : stored.equals(supplied));
  }

  @Transactional
  public void changePassword(HttpSession session, String oldPassword, String newPassword) {
    AppUser user = current(session);
    if (oldPassword == null || !matches(oldPassword, user.getPassword()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
    validatePassword(newPassword);
    if (matches(newPassword, user.getPassword()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a different password");
    user.setPassword(encoder.encode(newPassword));
    users.save(user);
  }

  @Transactional
  public void resetPassword(HttpSession session, Long userId) {
    manager(session);
    AppUser target = users.findById(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    if (target.getRole() == Role.MANAGER)
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot reset manager accounts");
    target.setPassword(encoder.encode("password"));
    users.save(target);
  }

  public java.util.List<java.util.Map<String, Object>> resettableUsers(HttpSession session) {
    manager(session);
    return users.findAll().stream().filter(u -> u.getRole() != Role.MANAGER)
        .map(u -> java.util.Map.<String, Object>of("id", u.getId(), "username", u.getUsername(), "role", u.getRole().name()))
        .toList();
  }

  private void validatePassword(String password) {
    if (password == null || password.length() < 8 || password.length() > 72 || password.isBlank())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be 8–72 characters");
  }

  public AppUser current(HttpSession session) {
    Object id = session.getAttribute(SESSION_USER_ID);
    if (id == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in");
    return users
        .findById((Long) id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired"));
  }

  public AppUser manager(HttpSession session) {
    AppUser u = current(session);
    if (u.getRole() != Role.MANAGER)
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager only");
    return u;
  }

  public Long teamId(HttpSession session) {
    AppUser u = current(session);
    if (u.getRole() == Role.MANAGER) return null;
    if (u.getTeamId() == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No team assigned");
    return u.getTeamId();
  }
}
