package io.github.ydhekim.crimson_sky.server.network.handler;

import com.badlogic.gdx.utils.Logger;
import io.github.ydhekim.crimson_sky.common.model.BattleMode;
import io.github.ydhekim.crimson_sky.common.model.MessageCode;
import io.github.ydhekim.crimson_sky.common.network.packet.AttackRejectedResponse;
import io.github.ydhekim.crimson_sky.common.network.packet.AttackRequest;
import io.github.ydhekim.crimson_sky.server.combat.AttackResult;
import io.github.ydhekim.crimson_sky.server.combat.RewardOutcome;
import io.github.ydhekim.crimson_sky.server.network.GameConnection;
import io.github.ydhekim.crimson_sky.server.network.RequestHandler;
import io.github.ydhekim.crimson_sky.server.service.AttackService;
import io.github.ydhekim.crimson_sky.server.service.RewardService;

import java.util.Optional;

/**
 * Resolves a whole battle for the requester (story B4, system design §6/§7).
 *
 * <p>One guardrail, the same one {@link CharacterListRequestHandler}/{@link DeleteCharacterRequestHandler}
 * already apply: the connection must be authenticated and {@code characterId} must belong to
 * {@code connection.account}. The old "is this character a participant in {@code battleId}" check is
 * gone with the packet that made it necessary — {@link AttackRequest} carries no battle or opponent id
 * for a client to misuse, since the server picks the opponent itself.
 *
 * <p>{@link AttackResult#toResponse(RewardOutcome)} is what reaches the client: the internal result's
 * {@code opponentIsBot} flag and the opponent's real character id are dropped there and never
 * serialized (§7).
 *
 * <p>Rewards are applied between resolving the battle and answering (story C1), so the response reports
 * a payout that is already committed. A failed reward transaction pays nothing but still sends the
 * battle — see {@link RewardService#applyRewards(AttackResult)}.
 */
public class AttackRequestHandler implements RequestHandler<AttackRequest> {

    private static final Logger log = new Logger("AttackRequestHandler", Logger.DEBUG);

    private final AttackService attackService;
    private final RewardService rewardService;

    public AttackRequestHandler(AttackService attackService, RewardService rewardService) {
        this.attackService = attackService;
        this.rewardService = rewardService;
    }

    @Override
    public void handle(GameConnection connection, AttackRequest request) {
        if (connection.account == null) {
            log.info("Rejected attack request from unauthenticated Connection ID: " + connection.getID());
            return;
        }

        if (!attackService.isCharacterOwnedBy(connection.account.id(), request.characterId())) {
            log.info("Rejected attack request: character " + request.characterId()
                + " is not owned by Account ID: " + connection.account.id()
                + " (Connection ID: " + connection.getID() + ")");
            return;
        }

        int remaining = attackService.remainingDailyBattles(request.characterId());
        if (remaining <= 0) {
            log.info("Rejected attack request: daily battle cap reached for character " + request.characterId()
                + " (Connection ID: " + connection.getID() + ")");
            connection.sendTCP(new AttackRejectedResponse(MessageCode.DAILY_BATTLE_CAP_REACHED.name()));
            return;
        }

        if (request.mode() == BattleMode.RANKED && !attackService.isRankedEligible(request.characterId())) {
            log.info("Rejected ranked attack request: character " + request.characterId()
                + " is below the level-25 gate (Connection ID: " + connection.getID() + ")");
            connection.sendTCP(new AttackRejectedResponse(MessageCode.RANKED_LEVEL_GATE_NOT_MET.name()));
            return;
        }

        Optional<AttackResult> result = attackService.attack(request.characterId(), request.mode());
        if (result.isEmpty()) {
            log.info("Dropped attack request for character " + request.characterId()
                + " (Connection ID: " + connection.getID() + ")");
            return;
        }

        RewardOutcome outcome = rewardService.applyRewards(result.get());

        log.info("Resolved battle " + result.get().battleId() + " for character " + request.characterId()
            + " (Connection ID: " + connection.getID() + ")");
        connection.sendTCP(result.get().toResponse(outcome));
    }
}
