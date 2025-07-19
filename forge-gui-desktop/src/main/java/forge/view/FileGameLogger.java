package forge.view;

import com.esotericsoftware.minlog.Log;
import com.google.common.eventbus.Subscribe;
import forge.ai.GameState;
import forge.game.Game;
import forge.game.GameLogEntry;
import forge.game.GameOutcome;
import forge.game.event.GameEvent;
import forge.game.event.GameEventGameOutcome;
import forge.game.event.GameEventSpellAbilityCast;
import forge.game.event.GameEventTurnPhase;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.item.IPaperCard;
import forge.model.FModel;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Observable;
import java.util.Observer;

/**
 * Logs game events and states to a file.
 */
public class FileGameLogger implements Observer, Closeable {
    private final BufferedWriter writer;
    private final Game game;

    public FileGameLogger(File file, Game game) throws IOException {
        this.writer = new BufferedWriter(new FileWriter(file, true));
        this.game = game;
        writePlayers();
    }

    private void writePlayers() throws IOException {
        writer.write("=== Players ===");
        writer.newLine();
        for (Player p : game.getRegisteredPlayers()) {
            RegisteredPlayer rp = p.getRegisteredPlayer();
            writer.write(p.getName() + " - " + rp.getDeck().getName());
            writer.newLine();
        }
        writer.newLine();
        writer.flush();
    }

    @Override
    public void update(Observable o, Object arg) {
        if (arg instanceof GameLogEntry) {
            try {
                writer.write(((GameLogEntry) arg).toString());
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                Log.debug("Exception in logger: " + e);
            }
        }
    }

    @Subscribe
    public void onTurnPhase(GameEventTurnPhase event) {
        // Do the printing of the event itself.
        onGameEvent(event);

        // And also print out the boars state.
        GameState state = new GameState() {
            @Override
            public IPaperCard getPaperCard(String cardName, String setCode, int artID) {
                return FModel.getMagicDb().getCommonCards().getCard(cardName, setCode, artID);
            }
        };
        try {
            state.initFromGame(event.playerTurn.getGame());
            writer.write("=== Board state snapshot ===");
            writer.newLine();
            writer.write(state.toString());
            writer.newLine();
            writer.flush();
        } catch (Exception e) {
            Log.debug("Exception in logger: " + e);
        }
    }

    @Subscribe
    public void onGameEvent(GameEvent event) {
        try {
            writer.write("== GameEvent: " + event.getClass().getName() + " ===");
            writer.newLine();
            writer.write(event.toString());
            writer.newLine();
            writer.flush();
        } catch (Exception e) {
            Log.debug("Exception in logger: " + e);
        }
    }


    @Override
    public void close() throws IOException {
        writer.close();
    }
}
