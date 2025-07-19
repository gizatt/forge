package forge.game.event;

import forge.util.Lang;
import forge.util.TextUtil;

public class GameEventGameFinished extends GameEvent {
    @Override
    public <T> T visit(IGameEventVisitor<T> visitor) {
        return visitor.visit(this);
    }

} // need this class to launch after log was built via previous event