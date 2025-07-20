package forge.view;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.time.StopWatch;

import java.io.IOException;
import forge.LobbyPlayer;
import forge.deck.Deck;
import forge.deck.DeckGroup;
import forge.deck.io.DeckSerializer;
import forge.game.Game;
import forge.game.GameEndReason;
import forge.game.GameLogEntry;
import forge.game.GameLogEntryType;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.Match;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.tournament.system.AbstractTournament;
import forge.gamemodes.tournament.system.TournamentBracket;
import forge.gamemodes.tournament.system.TournamentPairing;
import forge.gamemodes.tournament.system.TournamentPlayer;
import forge.gamemodes.tournament.system.TournamentRoundRobin;
import forge.gamemodes.tournament.system.TournamentSwiss;
import forge.localinstance.properties.ForgeConstants;
import forge.model.FModel;
import forge.player.GamePlayerUtil;
import com.google.common.collect.ImmutableSet;
import forge.ai.AIOption;
import forge.gui.GuiBase;
import forge.util.MyRandom;
import forge.util.Lang;
import forge.util.TextUtil;
import forge.util.WordUtil;
import forge.util.storage.IStorage;

public class SimulateMatch {
    public static void simulate(String[] args) {
        FModel.initialize(null, null);

        System.out.println("Simulation mode");
        if (args.length < 4) {
            argumentHelp();
            return;
        }

        final Map<String, List<String>> params = new HashMap<>();
        List<String> options = null;

        for (int i = 1; i < args.length; i++) {
            // "sim" is in the 0th slot
            final String a = args[i];

            if (a.charAt(0) == '-') {
                if (a.length() < 2) {
                    System.err.println("Error at argument " + a);
                    argumentHelp();
                    return;
                }

                options = new ArrayList<>();
                params.put(a.substring(1), options);
            } else if (options != null) {
                options.add(a);
            } else {
                System.err.println("Illegal parameter usage");
                return;
            }
        }

        int nGames = 1;
        if (params.containsKey("n")) {
            // Number of games should only be a single string
            nGames = Integer.parseInt(params.get("n").get(0));
        }

        int matchSize = 0;
        if (params.containsKey("m")) {
            // Match size ("best of X games")
            matchSize = Integer.parseInt(params.get("m").get(0));
        }

        boolean outputGamelog = !params.containsKey("q");
        boolean useSim = params.containsKey("useSim");
        String profileOverride = params.containsKey("profile")
                ? params.get("profile").get(0)
                : "";

        int aiTimeout = 5;
        if (params.containsKey("aiTimeout")) {
            aiTimeout = Integer.parseInt(params.get("aiTimeout").get(0));
        }

        int gameTimeout = 120;
        if (params.containsKey("gameTimeout")) {
            gameTimeout = Integer.parseInt(params.get("gameTimeout").get(0));
        }

        File logDir = null;
        if (params.containsKey("logDir")) {
            logDir = new File(params.get("logDir").get(0));
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
        }

        GameType type = GameType.Constructed;
        if (params.containsKey("f")) {
            type = GameType.valueOf(WordUtil.capitalize(params.get("f").get(0)));
        }

        GameRules rules = new GameRules(type);
        rules.setAppliedVariants(EnumSet.of(type));

        if (matchSize != 0) {
            rules.setGamesPerMatch(matchSize);
        }

        if (params.containsKey("t")) {
            simulateTournament(params, rules, outputGamelog);
            System.out.flush();
            return;
        }

        List<RegisteredPlayer> pp = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        int i = 1;

        if (params.containsKey("d")) {
            for (String deck : params.get("d")) {
                Deck d = deckFromCommandLineParameter(deck, type, params);
                if (d == null) {
                    System.out.println(TextUtil.concatNoSpace("Could not load deck - ", deck, ", match cannot start"));
                    return;
                }
                if (i > 1) {
                    sb.append(" vs ");
                }
                String name = TextUtil.concatNoSpace("Ai(", String.valueOf(i), ")-", d.getName());
                sb.append(name);

                RegisteredPlayer rp;

                if (type.equals(GameType.Commander)) {
                    rp = RegisteredPlayer.forCommander(d);
                } else {
                    rp = new RegisteredPlayer(d);
                }
                int sleeveCount = GuiBase.getInterface().getSleevesCount();
                int sleeveIndex = sleeveCount == 0 ? 0 : MyRandom.getRandom().nextInt(sleeveCount);
                rp.setPlayer(GamePlayerUtil.createAiPlayer(name, i - 1, sleeveIndex,
                        useSim ? ImmutableSet.of(AIOption.USE_SIMULATION) : null,
                        profileOverride));
                pp.add(rp);
                i++;
            }
        }

        sb.append(" - ").append(Lang.nounWithNumeral(nGames, "game")).append(" of ").append(type);

        System.out.println(sb.toString());

        Match mc = new Match(rules, pp, "Test");

        if (matchSize != 0) {
            int iGame = 0;
            while (!mc.isMatchOver()) {
                // play games until the match ends
                simulateSingleMatch(mc, iGame, outputGamelog, logDir, aiTimeout, gameTimeout);
                iGame++;
            }
        } else {
            for (int iGame = 0; iGame < nGames; iGame++) {
                simulateSingleMatch(mc, iGame, outputGamelog, logDir, aiTimeout, gameTimeout);
            }
        }

        System.out.flush();
    }

    private static void argumentHelp() {
        System.out.println("Syntax: forge.exe sim -d <deck1[.dck]> ... <deckX[.dck]> -D [D] -n [N] -m [M] -t [T] -p [P] -f [F] -aiTimeout [S] -gameTimeout [S] -useSim -profile [PROFILE] -q -logdir C:/logs");
        System.out.println("\tsim - stands for simulation mode");
        System.out.println("\tdeck1 (or deck2,...,X) - constructed deck name or filename (has to be quoted when contains multiple words)");
        System.out.println("\tdeck is treated as file if it ends with a dot followed by three numbers or letters");
        System.out.println("\tD - absolute directory to load decks from");
        System.out.println("\tN - number of games, defaults to 1 (Ignores match setting)");
        System.out.println("\tM - Play full match of X games, typically 1,3,5 games. (Optional, overrides N)");
        System.out.println("\tT - Type of tournament to run with all provided decks (Bracket, RoundRobin, Swiss)");
        System.out.println("\tP - Amount of players per match (used only with Tournaments, defaults to 2)");
        System.out.println("\tF - format of games, defaults to constructed");
        System.out.println("\taiTimeout - AI think time in seconds (<=0 disables timeout)");
        System.out.println("\tgameTimeout - Game timeout in seconds, default 120.");
        System.out.println("\tuseSim - Use simulation mode for AI players");
        System.out.println("\tprofile - Override the AI profile used by players");
        System.out.println("\tq - Quiet flag. Output just the game result, not the entire game log.");
        System.out.println("\t--logDir - directory to output structured game logs, one per game");
    }

    public static void simulateSingleMatch(final Match mc, int iGame, boolean outputGamelog) {
        simulateSingleMatch(mc, iGame, outputGamelog, null, 5, 120);
    }

    public static void simulateSingleMatch(final Match mc, int iGame, boolean outputGamelog, File logDir, int aiTimeout,  int gameTimeout) {
        final StopWatch sw = new StopWatch();
        sw.start();

        final Game g1 = mc.createGame();
        g1.AI_TIMEOUT = aiTimeout;
        g1.AI_CAN_USE_TIMEOUT = aiTimeout > 0;

        FileGameLogger logger = null;
        if (logDir != null) {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS").format(new Date());
            File logFile = new File(logDir, timestamp + "_game_" + iGame + ".log");
            try {
                logger = new FileGameLogger(logFile, g1);
                g1.subscribeToEvents(logger);
                g1.getGameLog().addObserver(logger);
            } catch (IOException e) {
                System.err.println("Failed to initialize logger: " + e.getMessage());
            }
        }

        // will run match in the same thread
        try {
            TimeLimitedCodeBlock.runWithTimeout(() -> {
                mc.startGame(g1);
                sw.stop();
            }, gameTimeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            System.out.println("Stopping slow match as draw");
        } catch (Exception | StackOverflowError e) {
            e.printStackTrace();
        } finally {
            if (sw.isStarted()) {
                sw.stop();
            }
            if (!g1.isGameOver()) {
                g1.setGameOver(GameEndReason.Draw);
            }
            if (logger != null) {
                try {
                    logger.close();
                } catch (IOException e) {
                    System.err.println("Failed to close logger: " + e.getMessage());
                }
            }
        }

        List<GameLogEntry> log;
        if (outputGamelog) {
            log = g1.getGameLog().getLogEntries(null);
        } else {
            log = g1.getGameLog().getLogEntries(GameLogEntryType.MATCH_RESULTS);
        }
        Collections.reverse(log);
        for (GameLogEntry l : log) {
            System.out.println(l);
        }

        // If both players life totals to 0 in a single turn, the game should end in a draw
        if (g1.getOutcome().isDraw()) {
            System.out.printf("\nGame Result: Game %d ended in a Draw! Took %d ms.%n", 1 + iGame, sw.getTime());
        } else {
            System.out.printf("\nGame Result: Game %d ended in %d ms. %s has won!\n%n", 1 + iGame, sw.getTime(), g1.getOutcome().getWinningLobbyPlayer().getName());
        }
    }

    private static void simulateTournament(Map<String, List<String>> params, GameRules rules, boolean outputGamelog) {
        String tournament = params.get("t").get(0);
        AbstractTournament tourney = null;
        int matchPlayers = params.containsKey("p") ? Integer.parseInt(params.get("p").get(0)) : 2;

        DeckGroup deckGroup = new DeckGroup("SimulatedTournament");
        List<TournamentPlayer> players = new ArrayList<>();
        int numPlayers = 0;
        if (params.containsKey("d")) {
            for (String deck : params.get("d")) {
                Deck d = deckFromCommandLineParameter(deck, rules.getGameType(), params);
                if (d == null) {
                    System.out.println(TextUtil.concatNoSpace("Could not load deck - ", deck, ", match cannot start"));
                    return;
                }

                deckGroup.addAiDeck(d);
                players.add(new TournamentPlayer(GamePlayerUtil.createAiPlayer(d.getName(), 0), numPlayers));
                numPlayers++;
            }
        }

        if (params.containsKey("D")) {
            // Direc
            String foldName = params.get("D").get(0);
            File folder = new File(foldName);
            if (!folder.isDirectory()) {
                System.out.println("Directory not found - " + foldName);
            } else {
                for (File deck : folder.listFiles((dir, name) -> name.endsWith(".dck"))) {
                    Deck d = DeckSerializer.fromFile(deck);
                    if (d == null) {
                        System.out.println(TextUtil.concatNoSpace("Could not load deck - ", deck.getName(), ", match cannot start"));
                        return;
                    }
                    deckGroup.addAiDeck(d);
                    players.add(new TournamentPlayer(GamePlayerUtil.createAiPlayer(d.getName(), 0), numPlayers));
                    numPlayers++;
                }
            }

        }

        if (numPlayers == 0) {
            System.out.println("No decks/Players found. Please try again.");
        }

        if ("bracket".equalsIgnoreCase(tournament)) {
            tourney = new TournamentBracket(players, matchPlayers);
        } else if ("roundrobin".equalsIgnoreCase(tournament)) {
            tourney = new TournamentRoundRobin(players, matchPlayers);
        } else if ("swiss".equalsIgnoreCase(tournament)) {
            tourney = new TournamentSwiss(players, matchPlayers);
        }
        if (tourney == null) {
            System.out.println("Failed to initialize tournament, bailing out");
            return;
        }

        tourney.initializeTournament();

        String lastWinner = "";
        int curRound = 0;
        System.out.println(TextUtil.concatNoSpace("Starting a ", tournament, " tournament with ",
                String.valueOf(numPlayers), " players over ",
                String.valueOf(tourney.getTotalRounds()), " rounds"));
        while (!tourney.isTournamentOver()) {
            if (tourney.getActiveRound() != curRound) {
                if (curRound != 0) {
                    System.out.println(TextUtil.concatNoSpace("End Round - ", String.valueOf(curRound)));
                }
                curRound = tourney.getActiveRound();
                System.out.println();
                System.out.println(TextUtil.concatNoSpace("Round ", String.valueOf(curRound), " Pairings:"));

                for (TournamentPairing pairing : tourney.getActivePairings()) {
                    System.out.println(pairing.outputHeader());
                }
                System.out.println();
            }

            TournamentPairing pairing = tourney.getNextPairing();
            List<RegisteredPlayer> regPlayers = AbstractTournament.registerTournamentPlayers(pairing, deckGroup);

            StringBuilder sb = new StringBuilder();
            sb.append("Round ").append(tourney.getActiveRound()).append(" - ");
            sb.append(pairing.outputHeader());
            System.out.println(sb.toString());

            if (!pairing.isBye()) {
                Match mc = new Match(rules, regPlayers, "TourneyMatch");

                int exceptions = 0;
                int iGame = 0;
                while (!mc.isMatchOver()) {
                    // play games until the match ends
                    try {
                        simulateSingleMatch(mc, iGame, outputGamelog);
                        iGame++;
                    } catch (Exception e) {
                        exceptions++;
                        System.out.println(e.toString());
                        if (exceptions > 5) {
                            System.out.println("Exceeded number of exceptions thrown. Abandoning match...");
                            break;
                        } else {
                            System.out.println("Game threw exception. Abandoning game and continuing...");
                        }
                    }

                }
                LobbyPlayer winner = mc.getWinner().getPlayer();
                for (TournamentPlayer tp : pairing.getPairedPlayers()) {
                    if (winner.equals(tp.getPlayer())) {
                        pairing.setWinner(tp);
                        lastWinner = winner.getName();
                        System.out.println(TextUtil.concatNoSpace("Match Winner - ", lastWinner, "!"));
                        System.out.println();
                        break;
                    }
                }
            }

            tourney.reportMatchCompletion(pairing);
        }
        tourney.outputTournamentResults();
    }
    public static Match simulateOffthreadGame(List<Deck> decks, GameType format, int games) {
        return null;
    }

    private static Deck deckFromCommandLineParameter(String deckname, GameType type, Map<String, List<String>> params) {
        int dotpos = deckname.lastIndexOf('.');
        if (dotpos > 0 && dotpos == deckname.length() - 4) {
            String baseDir;
            if (params.containsKey("D")){
                baseDir = params.get("D").get(0);
            } else {
                baseDir = type.equals(GameType.Commander) ?
                        ForgeConstants.DECK_COMMANDER_DIR : ForgeConstants.DECK_CONSTRUCTED_DIR;
            }

            File f = new File(baseDir + deckname);
            if (!f.exists()) {
                System.out.println("No deck found in " + baseDir);
            }

            return DeckSerializer.fromFile(f);
        }

        IStorage<Deck> deckStore = null;

        // Add other game types here...
        if (type.equals(GameType.Commander)) {
            deckStore = FModel.getDecks().getCommander();
        } else {
            deckStore = FModel.getDecks().getConstructed();
        }

        return deckStore.get(deckname);
    }

}