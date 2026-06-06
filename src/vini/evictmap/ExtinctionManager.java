package vini.evictmap;

import arc.util.Time;
import mindustry.game.Team;
import mindustry.gen.Call;

import java.util.ArrayList;
import java.util.List;

/**
 * Crown-on-the-hill late-game event.
 *
 * The clock starts after the first personal team claims a starting core.
 *
 * Timeline:
 * - 01:20:00: first warning; /over becomes unavailable
 * - 01:25:00: five-minute warning
 * - 01:29:00: one-minute warning
 * - 01:30:00: outermost live ring collapses immediately
 * - every 01:30 afterward: the next live ring collapses
 * - when the center plus its six neighbours remain: four-minute center hold
 * - if the center is still Fallen after four minutes: overtime until captured
 */
final class ExtinctionManager {

    private static final float WARNING_TEN_MINUTES_TICKS = 80f * 60f * 60f;
    private static final float WARNING_FIVE_MINUTES_TICKS = 85f * 60f * 60f;
    private static final float WARNING_ONE_MINUTE_TICKS = 89f * 60f * 60f;
    private static final float EXTINCTION_START_TICKS = 90f * 60f * 60f;

    private static final float RING_INTERVAL_TICKS = 90f * 60f;
    private static final float FINAL_HOLD_TICKS = 4f * 60f * 60f;

    private final TeamManager teamManager;

    private float elapsedTicks = 0f;
    private float nextRingCollapseTicks = Float.POSITIVE_INFINITY;
    private float finalPhaseEndTicks = Float.POSITIVE_INFINITY;

    private boolean warningTenMinutesSent = false;
    private boolean warningFiveMinutesSent = false;
    private boolean warningOneMinuteSent = false;
    private boolean extinctionStarted = false;
    private boolean finalPhase = false;
    private boolean overtime = false;

    ExtinctionManager(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    void beginRound() {
        elapsedTicks = 0f;
        nextRingCollapseTicks = Float.POSITIVE_INFINITY;
        finalPhaseEndTicks = Float.POSITIVE_INFINITY;

        warningTenMinutesSent = false;
        warningFiveMinutesSent = false;
        warningOneMinuteSent = false;
        extinctionStarted = false;
        finalPhase = false;
        overtime = false;

        teamManager.setExtinctionActive(false);
    }

    void update() {
        if (
            !teamManager.isRoundActiveForSystems()
                || !teamManager.isRoundActivated()
        ) {
            return;
        }

        elapsedTicks += Time.delta;

        if (
            !warningTenMinutesSent
                && elapsedTicks >= WARNING_TEN_MINUTES_TICKS
        ) {
            warningTenMinutesSent = true;

            Call.sendMessage(
                "[scarlet]EXTINCTION begins in 10 minutes. "
                    + "Outer hexes will collapse toward the center.[]"
            );
        }

        if (
            !warningFiveMinutesSent
                && elapsedTicks >= WARNING_FIVE_MINUTES_TICKS
        ) {
            warningFiveMinutesSent = true;

            Call.sendMessage(
                "[scarlet]EXTINCTION begins in 5 minutes.[]"
            );
        }

        if (
            !warningOneMinuteSent
                && elapsedTicks >= WARNING_ONE_MINUTE_TICKS
        ) {
            warningOneMinuteSent = true;

            Call.sendMessage(
                "[scarlet]EXTINCTION begins in 1 minute.[]"
            );
        }

        if (!extinctionStarted && elapsedTicks >= EXTINCTION_START_TICKS) {
            startExtinction();
        }

        if (extinctionStarted && !finalPhase) {
            while (
                !finalPhase
                    && teamManager.isRoundActiveForSystems()
                    && elapsedTicks >= nextRingCollapseTicks
            ) {
                collapseNextRing();
            }
        }

        if (!finalPhase || !teamManager.isRoundActiveForSystems()) {
            return;
        }

        if (!overtime && elapsedTicks >= finalPhaseEndTicks) {
            Team centerOwner = teamManager.centerHexOwner();

            if (validCrownWinner(centerOwner)) {
                teamManager.finishExtinction(centerOwner, false);
                return;
            }

            overtime = true;

            Call.sendMessage(
                "[scarlet]EXTINCTION OVERTIME: the center core is still Fallen. "
                    + "Capture it to win immediately.[]"
            );
        }

        if (overtime) {
            Team centerOwner = teamManager.centerHexOwner();

            if (validCrownWinner(centerOwner)) {
                teamManager.finishExtinction(centerOwner, true);
            }
        }
    }

    boolean blocksEarlyEnd() {
        return warningTenMinutesSent || elapsedTicks >= WARNING_TEN_MINUTES_TICKS;
    }

    private void startExtinction() {
        extinctionStarted = true;
        teamManager.setExtinctionActive(true);

        Call.sendMessage(
            "[scarlet]EXTINCTION HAS STARTED. "
                + "The outermost hex ring is collapsing now.[]"
        );

        collapseNextRing();
    }

    private void collapseNextRing() {
        int outermostDistance = -1;

        for (TeamManager.HexSlot slot : teamManager.slots()) {
            if (!slot.extinct) {
                outermostDistance = Math.max(
                    outermostDistance,
                    teamManager.gridDistanceFromCenter(slot)
                );
            }
        }

        if (outermostDistance <= 1) {
            startFinalPhase();
            return;
        }

        List<TeamManager.HexSlot> collapsing = new ArrayList<>();

        for (TeamManager.HexSlot slot : teamManager.slots()) {
            if (
                !slot.extinct
                    && teamManager.gridDistanceFromCenter(slot)
                        == outermostDistance
            ) {
                collapsing.add(slot);
            }
        }

        teamManager.collapseHexesForExtinction(collapsing);

        if (!teamManager.isRoundActiveForSystems()) {
            return;
        }

        if (onlyFinalSevenRemain()) {
            startFinalPhase();
            return;
        }

        nextRingCollapseTicks = elapsedTicks + RING_INTERVAL_TICKS;

        Call.sendMessage(
            "[scarlet]EXTINCTION: ring "
                + outermostDistance
                + " collapsed. The next ring collapses in 90 seconds.[]"
        );
    }

    private boolean onlyFinalSevenRemain() {
        for (TeamManager.HexSlot slot : teamManager.slots()) {
            if (
                !slot.extinct
                    && teamManager.gridDistanceFromCenter(slot) > 1
            ) {
                return false;
            }
        }

        return true;
    }

    private void startFinalPhase() {
        if (finalPhase || !teamManager.isRoundActiveForSystems()) {
            return;
        }

        finalPhase = true;
        finalPhaseEndTicks = elapsedTicks + FINAL_HOLD_TICKS;

        Call.sendMessage(
            "[accent]FINAL EXTINCTION PHASE: only the center and its six "
                + "neighbouring hexes remain. Hold the center core. "
                + "Its owner in 4 minutes wins the round.[]"
        );
    }

    private boolean validCrownWinner(Team team) {
        return team != null
            && team != TeamManager.FALLEN_TEAM
            && team != Team.derelict;
    }
}
