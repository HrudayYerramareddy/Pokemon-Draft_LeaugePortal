package com.pokemondraft.leagueportal.services;

import com.pokemondraft.leagueportal.entity.*;
import com.pokemondraft.leagueportal.repositories.AppUserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
  public static final String SESSION_USER_ID = "userId";
  private final AppUserRepository users;

  public AuthService(AppUserRepository users) {
    this.users = users;
  }

  public AppUser login(String username, String password, HttpSession session) {
    AppUser user =
        users
            .findByUsername(username)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid login"));
    if (!user.getPassword().equals(password))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid login");
    session.setAttribute(SESSION_USER_ID, user.getId());
    return user;
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
