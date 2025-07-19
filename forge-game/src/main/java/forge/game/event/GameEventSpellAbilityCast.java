package forge.game.event;

import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.util.TextUtil;

/** 
 * TODO: Write javadoc for this type.
 *
 */
public class GameEventSpellAbilityCast extends GameEvent {

    public final SpellAbility sa; 
    public final SpellAbilityStackInstance si;
    public final int stackIndex;

    public GameEventSpellAbilityCast(SpellAbility sp, SpellAbilityStackInstance si, int stackIndex) {
        sa = sp;
        this.si = si;
        this.stackIndex = stackIndex;
    }

    /* (non-Javadoc)
     * @see forge.game.event.GameEvent#visit(forge.game.event.IGameEventVisitor)
     */
    @Override
    public <T> T visit(IGameEventVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        return TextUtil.concatNoSpace("spellability=", sa.toUnsuppressedString(), "\n") +
                TextUtil.concatNoSpace("stackinstance=", si.toString(), "\n") +
                TextUtil.concatNoSpace("stackindex=", String.valueOf(stackIndex), "\n");
    }
}
