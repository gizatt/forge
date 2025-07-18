package forge.view;

import com.google.common.eventbus.Subscribe;
import forge.ai.GameState;
import forge.game.GameLogEntry;
import forge.game.event.GameEventTurnPhase;
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

    public FileGameLogger(File file) throws IOException {
        this.writer = new BufferedWriter(new FileWriter(file, true));
    }

    @Override
    public void update(Observable o, Object arg) {
        if (arg instanceof GameLogEntry) {
            try {
                writer.write(((GameLogEntry) arg).toString());
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                // ignore logging failures
            }
        }
    }

    @Subscribe
    public void onTurnPhase(GameEventTurnPhase event) {
        GameState state = new GameState() {
            @Override
            public IPaperCard getPaperCard(String cardName, String setCode, int artID) {
                return FModel.getMagicDb().getCommonCards().getCard(cardName, setCode, artID);
            }
        };
        try {
            state.initFromGame(event.playerTurn.getGame());
            writer.write(state.toString());
            writer.newLine();
            writer.flush();
        } catch (Exception e) {
            // ignore logging failures
        }
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
