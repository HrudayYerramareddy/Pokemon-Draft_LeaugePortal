package com.pokemondraft.leagueportal.controller;

import com.pokemondraft.leagueportal.model.AppUser;
import com.pokemondraft.leagueportal.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth){this.auth=auth;}

    public record LoginRequest(String username,String password){}
    @PostMapping("/login")
    public Map<String,Object> login(@RequestBody LoginRequest req,HttpSession session){return safe(auth.login(req.username(),req.password(),session));}
    @PostMapping("/logout")
    public void logout(HttpSession session){session.invalidate();}
    @GetMapping("/me")
    public Map<String,Object> me(HttpSession session){return safe(auth.current(session));}
    private Map<String,Object> safe(AppUser u){return Map.of("id",u.getId(),"username",u.getUsername(),"role",u.getRole().name(),"teamId",u.getTeamId()==null?"":u.getTeamId());}
}
