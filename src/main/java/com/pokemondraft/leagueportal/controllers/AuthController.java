package com.pokemondraft.leagueportal.controllers;

import com.pokemondraft.leagueportal.entity.AppUser;
import com.pokemondraft.leagueportal.services.AuthService;
import jakarta.servlet.http.HttpSession;
import com.pokemondraft.leagueportal.dto.UserDto;
import com.pokemondraft.leagueportal.mapper.LeagueMapper;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  public record LoginRequest(String username, String password) {}

  @PostMapping("/login")
  public UserDto login(@RequestBody LoginRequest req, HttpSession session) {
    return LeagueMapper.authUser(auth.login(req.username(), req.password(), session));
  }

  public record ChangePasswordRequest(String currentPassword, String newPassword) {}

  @PostMapping("/change-password")
  public void changePassword(@RequestBody ChangePasswordRequest request, HttpSession session) {
    auth.changePassword(session, request.currentPassword(), request.newPassword());
  }

  @GetMapping("/manager/users")
  public java.util.List<java.util.Map<String, Object>> resettableUsers(HttpSession session) {
    return auth.resettableUsers(session);
  }

  @PostMapping("/manager/users/{id}/reset-password")
  public void resetPassword(@PathVariable Long id, HttpSession session) {
    auth.resetPassword(session, id);
  }

  @PostMapping("/logout")
  public void logout(HttpSession session) {
    session.invalidate();
  }

  @GetMapping("/me")
  public UserDto me(HttpSession session) {
    return LeagueMapper.authUser(auth.current(session));
  }

}
