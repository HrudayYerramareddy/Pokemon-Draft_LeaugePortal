package com.pokemondraft.leagueportal.controller;

import com.pokemondraft.leagueportal.service.AuthService;
import com.pokemondraft.leagueportal.service.TestRosterService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/manager/testing")
public class TestRosterController {
    private final AuthService auth;
    private final TestRosterService testRosters;

    public TestRosterController(AuthService auth, TestRosterService testRosters) {
        this.auth = auth;
        this.testRosters = testRosters;
    }

    @PostMapping("/fill-rosters")
    public Map<String,Integer> fillRosters(HttpSession session) {
        auth.manager(session);
        return testRosters.fillTestRosters();
    }

    @PostMapping("/clear-rosters")
    public Map<String,Integer> clearRosters(HttpSession session) {
        auth.manager(session);
        return testRosters.clearRosters();
    }
}
