package com.pokemondraft.leagueportal.controller;

import com.pokemondraft.leagueportal.model.*;
import com.pokemondraft.leagueportal.repository.*;
import com.pokemondraft.leagueportal.service.*;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ApiController {
  private final AuthService auth;
  private final TeamRepository teams;
  private final PokemonRepository pokemon;
  private final RosterEntryRepository roster;
  private final DraftPickRepository picks;
  private final DraftService draft;
  private final MatchupRepository matchups;
  private final ScheduleService schedule;
  private final LeagueSettingsRepository settingsRepo;
  private final WeeklySubmissionRepository submissions;
  private final FreeAgentBidRepository bids;
  private final FreeAgencyService freeAgency;
  private final TradeOfferRepository trades;
  private final TransactionRecordRepository transactions;

  public ApiController(
      AuthService auth,
      TeamRepository teams,
      PokemonRepository pokemon,
      RosterEntryRepository roster,
      DraftPickRepository picks,
      DraftService draft,
      MatchupRepository matchups,
      ScheduleService schedule,
      LeagueSettingsRepository settingsRepo,
      WeeklySubmissionRepository submissions,
      FreeAgentBidRepository bids,
      FreeAgencyService freeAgency,
      TradeOfferRepository trades,
      TransactionRecordRepository transactions) {
    this.auth = auth;
    this.teams = teams;
    this.pokemon = pokemon;
    this.roster = roster;
    this.picks = picks;
    this.draft = draft;
    this.matchups = matchups;
    this.schedule = schedule;
    this.settingsRepo = settingsRepo;
    this.submissions = submissions;
    this.bids = bids;
    this.freeAgency = freeAgency;
    this.trades = trades;
    this.transactions = transactions;
  }

  @GetMapping("/dashboard")
  public Map<String, Object> dashboard(HttpSession session) {
    AppUser u = auth.current(session);
    LeagueSettings s = settings();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("user", safeUser(u));
    out.put("settings", settingsMap(s));
    out.put("teams", teams.findAll());
    if (u.getTeamId() != null) out.put("myRoster", rosterView(u.getTeamId()));
    out.put("currentDraftTeam", draft.currentTeam());
    return out;
  }

  @GetMapping("/teams")
  public List<Team> teams(HttpSession session) {
    auth.current(session);
    return teams.findAll().stream()
        .sorted(Comparator.comparing(Team::getDivision).thenComparingInt(Team::getDraftPosition))
        .toList();
  }

  public record TeamUpdate(
      String name,
      String coachName,
      String division,
      Integer faBudget,
      Integer draftPosition,
      Integer manualRank) {}

  @PutMapping("/manager/teams/{id}")
  public Team updateTeam(@PathVariable Long id, @RequestBody TeamUpdate r, HttpSession session) {
    auth.manager(session);
    Team t = teams.findById(id).orElseThrow();
    if (r.name() != null && !r.name().isBlank()) t.setName(r.name());
    if (r.coachName() != null) t.setCoachName(r.coachName());
    if (r.division() != null && (r.division().equals("A") || r.division().equals("B")))
      t.setDivision(r.division());
    if (r.faBudget() != null) t.setFaBudget(r.faBudget());
    if (r.draftPosition() != null) t.setDraftPosition(r.draftPosition());
    if (r.manualRank() != null) t.setManualRank(r.manualRank());
    return teams.save(t);
  }

  @GetMapping("/pokemon")
  public List<Pokemon> allPokemon(HttpSession session) {
    auth.current(session);
    return pokemon.findAllByOrderByNameAsc();
  }

  @GetMapping("/pokemon/free-agents")
  public List<Pokemon> freeAgents(HttpSession session) {
    auth.current(session);
    return pokemon.findByDraftedFalseOrderByNameAsc();
  }

  public record PriceUpdate(int price) {}

  @PutMapping("/manager/pokemon/{id}/price")
  public Pokemon price(@PathVariable Long id, @RequestBody PriceUpdate r, HttpSession session) {
    auth.manager(session);
    if (r.price() < 0 || r.price() > 20)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Price must be $0-$20");
    Pokemon p = pokemon.findById(id).orElseThrow();
    p.setPrice(r.price());
    return pokemon.save(p);
  }

  @GetMapping("/rosters")
  public List<Map<String, Object>> rosters(HttpSession session) {
    auth.current(session);
    return teams.findAll().stream()
        .sorted(Comparator.comparing(Team::getName))
        .map(
            t -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("team", t);
              m.put("pokemon", rosterView(t.getId()));
              return m;
            })
        .toList();
  }

  @GetMapping("/rosters/{teamId}")
  public List<Map<String, Object>> roster(@PathVariable Long teamId, HttpSession session) {
    auth.current(session);
    return rosterView(teamId);
  }

  @GetMapping("/draft")
  public Map<String, Object> draftState(HttpSession session) {
    auth.current(session);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("currentTeam", draft.currentTeam());
    m.put("picks", pickViews());
    m.put("available", pokemon.findByDraftedFalseOrderByNameAsc());
    m.put("settings", settingsMap(settings()));
    return m;
  }

  public record PickRequest(Long pokemonId) {}

  @PostMapping("/draft/pick")
  public DraftPick pick(@RequestBody PickRequest r, HttpSession session) {
    AppUser u = auth.current(session);
    return draft.pick(u.getTeamId(), u.getRole() == Role.MANAGER, r.pokemonId());
  }

  @PostMapping("/manager/draft/undo")
  public void undo(HttpSession session) {
    auth.manager(session);
    draft.undoLast();
  }

  @GetMapping("/standings")
  public Map<String, Object> standings(HttpSession session) {
    auth.current(session);
    return Map.of(
        "A",
        schedule.standings("A"),
        "B",
        schedule.standings("B"),
        "seedModeEnabled",
        settings().isPlayoffSeedModeEnabled());
  }

  @GetMapping("/schedule")
  public List<Map<String, Object>> schedule(HttpSession session) {
    auth.current(session);
    Map<Long, String> names = teamNames();
    return matchups.findAllByOrderByWeekAscIdAsc().stream()
        .map(m -> matchupView(m, names))
        .toList();
  }

  @PostMapping("/manager/schedule/generate")
  public void generate(HttpSession session) {
    auth.manager(session);
    schedule.generate();
  }

  public record ScheduleSeed(long seed) {}

  @PostMapping("/manager/schedule/regenerate")
  public void regenerate(@RequestBody ScheduleSeed r, HttpSession session) {
    auth.manager(session);
    LeagueSettings s = settings();
    s.setScheduleSeed(r.seed());
    settingsRepo.save(s);
    schedule.generate();
  }

  public record MatchupEdit(Integer week, Long homeTeamId, Long awayTeamId) {}

  @PutMapping("/manager/schedule/{id}")
  public Matchup editMatchup(
      @PathVariable Long id, @RequestBody MatchupEdit r, HttpSession session) {
    auth.manager(session);
    Matchup m = matchups.findById(id).orElseThrow();
    if (r.week() != null) m.setWeek(r.week());
    if (r.homeTeamId() != null) m.setHomeTeamId(r.homeTeamId());
    if (r.awayTeamId() != null) m.setAwayTeamId(r.awayTeamId());
    return matchups.save(m);
  }

  public record ResultRequest(int homeScore, int awayScore) {}

  @PostMapping("/manager/results/{id}")
  public void result(@PathVariable Long id, @RequestBody ResultRequest r, HttpSession session) {
    auth.manager(session);
    schedule.setResult(id, r.homeScore(), r.awayScore());
  }

  @DeleteMapping("/manager/results/{id}")
  public void clearResult(@PathVariable Long id, HttpSession session) {
    auth.manager(session);
    schedule.clearResult(id);
  }

  @GetMapping("/lineup")
  public Map<String, Object> lineup(HttpSession session) {
    AppUser u = auth.current(session);
    if (u.getRole() == Role.MANAGER)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Manager has no team lineup");
    LeagueSettings s = settings();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("week", s.getCurrentWeek());
    out.put("deadline", s.getLineupDeadline());
    out.put("roster", rosterView(u.getTeamId()));
    out.put(
        "submission",
        submissions.findByTeamIdAndWeek(u.getTeamId(), s.getCurrentWeek()).orElse(null));
    return out;
  }

  public record LineupRequest(List<Long> pokemonIds) {}

  @PostMapping("/lineup")
  public WeeklySubmission submitLineup(@RequestBody LineupRequest r, HttpSession session) {
    AppUser u = auth.current(session);
    if (u.getRole() == Role.MANAGER)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Manager has no team lineup");
    LeagueSettings s = settings();
    if (s.getLineupDeadline() != null && LocalDateTime.now().isAfter(s.getLineupDeadline()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lineup deadline has passed");
    if (r.pokemonIds() == null || new HashSet<>(r.pokemonIds()).size() != 6)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select exactly 6 unique Pokemon");
    Set<Long> mine =
        roster.findByTeamId(u.getTeamId()).stream()
            .map(RosterEntry::getPokemonId)
            .collect(Collectors.toSet());
    if (!mine.containsAll(r.pokemonIds()))
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Lineup contains a Pokemon not on your roster");
    String csv = r.pokemonIds().stream().map(String::valueOf).collect(Collectors.joining(","));
    WeeklySubmission ws =
        submissions
            .findByTeamIdAndWeek(u.getTeamId(), s.getCurrentWeek())
            .orElse(new WeeklySubmission(u.getTeamId(), s.getCurrentWeek(), csv));
    ws.setPokemonIdsCsv(csv);
    ws.setSubmittedAt(LocalDateTime.now());
    return submissions.save(ws);
  }

  @GetMapping("/lineups/week/{week}")
  public List<Map<String, Object>> weekLineups(@PathVariable int week, HttpSession session) {
    AppUser u = auth.current(session);
    LeagueSettings s = settings();
    boolean reveal =
        u.getRole() == Role.MANAGER
            || (s.getLineupDeadline() != null
                && LocalDateTime.now().isAfter(s.getLineupDeadline()));
    if (!reveal)
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Lineups stay hidden until the deadline");
    return submissions.findByWeek(week).stream().map(this::submissionView).toList();
  }

  public record BidRequest(Long wantedPokemonId, Long dropPokemonId, int amount) {}

  @PostMapping("/free-agency/bids")
  public FreeAgentBid bid(@RequestBody BidRequest r, HttpSession session) {
    AppUser u = auth.current(session);
    if (u.getRole() == Role.MANAGER)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Manager cannot bid");
    return freeAgency.submitBid(u.getTeamId(), r.wantedPokemonId(), r.dropPokemonId(), r.amount());
  }

  @GetMapping("/free-agency/bids")
  public List<FreeAgentBid> bids(HttpSession session) {
    AppUser u = auth.current(session);
    return u.getRole() == Role.MANAGER
        ? bids.findAll()
        : bids.findByTeamIdOrderByCreatedAtDesc(u.getTeamId());
  }

  @PostMapping("/manager/free-agency/process")
  public Map<String, Integer> process(HttpSession session) {
    auth.manager(session);
    return Map.of("winners", freeAgency.processBids());
  }

  public record TradeRequest(
      Long recipientTeamId,
      List<Long> offeredPokemonIds,
      List<Long> requestedPokemonIds,
      Integer proposerFaab,
      Integer recipientFaab) {}

  @PostMapping("/trades")
  public TradeOffer trade(@RequestBody TradeRequest r, HttpSession session) {
    AppUser u = auth.current(session);
    if (u.getRole() == Role.MANAGER)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Manager cannot offer trade");
    return freeAgency.offerTrade(
        u.getTeamId(),
        r.recipientTeamId(),
        r.offeredPokemonIds(),
        r.requestedPokemonIds(),
        r.proposerFaab() == null ? 0 : r.proposerFaab(),
        r.recipientFaab() == null ? 0 : r.recipientFaab());
  }

  @GetMapping("/trades")
  public List<TradeOffer> trades(HttpSession session) {
    AppUser u = auth.current(session);
    return u.getRole() == Role.MANAGER
        ? trades.findAll()
        : trades.findByProposerTeamIdOrRecipientTeamIdOrderByCreatedAtDesc(
            u.getTeamId(), u.getTeamId());
  }

  public record TradeResponse(boolean accept) {}

  @PostMapping("/trades/{id}/respond")
  public TradeOffer respond(
      @PathVariable Long id, @RequestBody TradeResponse r, HttpSession session) {
    AppUser u = auth.current(session);
    return freeAgency.respondTrade(id, u.getTeamId(), u.getRole() == Role.MANAGER, r.accept());
  }

  @GetMapping("/transactions")
  public List<TransactionRecord> transactions(HttpSession session) {
    auth.current(session);
    return transactions.findAllByOrderByCreatedAtDesc();
  }

  @GetMapping("/settings")
  public Map<String, Object> settingsGet(HttpSession session) {
    auth.current(session);
    return settingsMap(settings());
  }

  public record SettingsUpdate(
      String leagueName,
      Integer currentWeek,
      Integer regularSeasonWeeks,
      Boolean draftOpen,
      Boolean snakeDraft,
      Long scheduleSeed,
      String lineupDeadline) {}

  @PutMapping("/manager/settings")
  public Map<String, Object> updateSettings(@RequestBody SettingsUpdate r, HttpSession session) {
    auth.manager(session);
    LeagueSettings s = settings();
    if (r.leagueName() != null) s.setLeagueName(r.leagueName());
    if (r.currentWeek() != null) s.setCurrentWeek(r.currentWeek());
    if (r.regularSeasonWeeks() != null) s.setRegularSeasonWeeks(r.regularSeasonWeeks());
    if (r.draftOpen() != null) s.setDraftOpen(r.draftOpen());
    if (r.snakeDraft() != null) s.setSnakeDraft(r.snakeDraft());
    if (r.scheduleSeed() != null) s.setScheduleSeed(r.scheduleSeed());
    if (r.lineupDeadline() != null)
      s.setLineupDeadline(
          r.lineupDeadline().isBlank() ? null : LocalDateTime.parse(r.lineupDeadline()));
    settingsRepo.save(s);
    return settingsMap(s);
  }

  private LeagueSettings settings() {
    return settingsRepo.findById(1L).orElseGet(() -> settingsRepo.save(new LeagueSettings()));
  }

  private Map<String, Object> safeUser(AppUser u) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", u.getId());
    m.put("username", u.getUsername());
    m.put("role", u.getRole().name());
    m.put("teamId", u.getTeamId());
    return m;
  }

  private Map<String, Object> settingsMap(LeagueSettings s) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("leagueName", s.getLeagueName());
    m.put("currentWeek", s.getCurrentWeek());
    m.put("regularSeasonWeeks", s.getRegularSeasonWeeks());
    m.put("draftOpen", s.isDraftOpen());
    m.put("snakeDraft", s.isSnakeDraft());
    m.put("scheduleSeed", s.getScheduleSeed());
    m.put("lineupDeadline", s.getLineupDeadline());
    m.put("playoffSeedModeEnabled", s.isPlayoffSeedModeEnabled());
    return m;
  }

  private Map<Long, String> teamNames() {
    return teams.findAll().stream().collect(Collectors.toMap(Team::getId, Team::getName));
  }

  private List<Map<String, Object>> rosterView(Long teamId) {
    Map<Long, Pokemon> p =
        pokemon.findAll().stream().collect(Collectors.toMap(Pokemon::getId, x -> x));
    return roster.findByTeamId(teamId).stream()
        .map(
            e -> {
              Pokemon mon = p.get(e.getPokemonId());
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("entryId", e.getId());
              m.put("pokemonId", e.getPokemonId());
              m.put("name", mon == null ? "Unknown" : mon.getName());
              m.put("price", mon == null ? 0 : mon.getPrice());
              return m;
            })
        .sorted(Comparator.comparing(x -> String.valueOf(x.get("name"))))
        .toList();
  }

  private List<Map<String, Object>> pickViews() {
    Map<Long, String> tn = teamNames();
    Map<Long, String> pn =
        pokemon.findAll().stream().collect(Collectors.toMap(Pokemon::getId, Pokemon::getName));
    return picks.findAllByOrderByOverallPickAsc().stream()
        .map(
            p -> {
              Map<String, Object> m = new LinkedHashMap<>();
              m.put("overallPick", p.getOverallPick());
              m.put("round", p.getRoundNumber());
              m.put("teamId", p.getTeamId());
              m.put("teamName", tn.get(p.getTeamId()));
              m.put("pokemonId", p.getPokemonId());
              m.put("pokemonName", pn.get(p.getPokemonId()));
              return m;
            })
        .toList();
  }

  private Map<String, Object> matchupView(Matchup m, Map<Long, String> names) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", m.getId());
    out.put("week", m.getWeek());
    out.put("homeTeamId", m.getHomeTeamId());
    out.put("awayTeamId", m.getAwayTeamId());
    out.put("homeTeam", names.get(m.getHomeTeamId()));
    out.put("awayTeam", names.get(m.getAwayTeamId()));
    out.put("homeScore", m.getHomeScore());
    out.put("awayScore", m.getAwayScore());
    out.put("played", m.isPlayed());
    return out;
  }

  private Map<String, Object> submissionView(WeeklySubmission s) {
    Map<Long, String> pn =
        pokemon.findAll().stream().collect(Collectors.toMap(Pokemon::getId, Pokemon::getName));
    List<String> names =
        Arrays.stream(s.getPokemonIdsCsv().split(","))
            .filter(x -> !x.isBlank())
            .map(Long::valueOf)
            .map(id -> pn.getOrDefault(id, "Unknown"))
            .toList();
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("teamId", s.getTeamId());
    m.put("teamName", teams.findById(s.getTeamId()).map(Team::getName).orElse("Unknown"));
    m.put("week", s.getWeek());
    m.put("pokemon", names);
    m.put("submittedAt", s.getSubmittedAt());
    return m;
  }
}
