package forge.game.event;

import java.util.Collection;

import forge.game.GameOutcome;
import forge.util.Lang;
import forge.util.TextUtil;

public class GameEventGameOutcome extends GameEvent {
    public final GameOutcome result;
    public final Collection<GameOutcome> history;

    public GameEventGameOutcome(GameOutcome lastOne, Collection<GameOutcome> history) {
        this.result = lastOne;
        this.history = history;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(TextUtil.concatNoSpace("result=", result.toString(), "\n"));
        return sb.toString();
    }

    @Override
    public <T> T visit(IGameEventVisitor<T> visitor) {
        return visitor.visit(this);
    }
}