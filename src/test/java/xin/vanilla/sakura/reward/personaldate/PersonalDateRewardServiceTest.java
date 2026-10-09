package xin.vanilla.sakura.reward.personaldate;

import com.google.gson.JsonObject;
import org.junit.Test;
import xin.vanilla.sakura.api.reward.SakuraRewardTypes;
import xin.vanilla.sakura.data.PlayerSignInData;
import xin.vanilla.sakura.data.calendar.CalendarIds;
import xin.vanilla.sakura.data.personaldate.PersonalDateDeliveryMode;
import xin.vanilla.sakura.data.personaldate.PersonalDatePreset;
import xin.vanilla.sakura.data.personaldate.PersonalDateRecurrence;
import xin.vanilla.sakura.data.personaldate.PlayerPersonalDateSlot;
import xin.vanilla.sakura.reward.Reward;
import xin.vanilla.sakura.reward.RewardList;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class PersonalDateRewardServiceTest {
    private final PersonalDateRewardService service = new PersonalDateRewardService();

    @Test
    public void grantsMatchingDeliveryModeOnceAndAdvancesCursor() {
        PlayerSignInData data = playerData();
        PersonalDatePreset preset = preset(PersonalDateDeliveryMode.SIGN_IN);
        AtomicInteger grants = new AtomicInteger();

        PersonalDateDeliveryResult first = service.deliver(
                PersonalDateDeliveryMode.SIGN_IN,
                LocalDate.of(2026, 8, 15),
                Collections.singletonList(preset), data,
                (reward, occurrence) -> grants.incrementAndGet()
        );
        PersonalDateDeliveryResult repeated = service.deliver(
                PersonalDateDeliveryMode.SIGN_IN,
                LocalDate.of(2026, 8, 15),
                Collections.singletonList(preset), data,
                (reward, occurrence) -> grants.incrementAndGet()
        );

        assertEquals(1, first.getClaimedOccurrences());
        assertEquals(1, first.getProcessedRewards());
        assertEquals(0, repeated.getClaimedOccurrences());
        assertEquals(1, grants.get());
        assertEquals("YEARLY:2026",
                data.getPersonalDateSlots().get(0).getLastClaimedOccurrenceKey());
    }

    @Test
    public void ignoresPresetsForAnotherDeliveryMode() {
        PlayerSignInData data = playerData();

        PersonalDateDeliveryResult result = service.deliver(
                PersonalDateDeliveryMode.ONLINE,
                LocalDate.of(2026, 8, 15),
                Collections.singletonList(preset(PersonalDateDeliveryMode.SIGN_IN)),
                data, (reward, occurrence) -> {
                }
        );

        assertEquals(0, result.getClaimedOccurrences());
        assertEquals("", data.getPersonalDateSlots().get(0).getLastClaimedOccurrenceKey());
    }

    @Test(expected = IllegalStateException.class)
    public void leavesCursorUntouchedWhenGrantPipelineThrows() {
        PlayerSignInData data = playerData();
        try {
            service.deliver(
                    PersonalDateDeliveryMode.SIGN_IN,
                    LocalDate.of(2026, 8, 15),
                    Collections.singletonList(preset(PersonalDateDeliveryMode.SIGN_IN)),
                    data, (reward, occurrence) -> {
                        throw new IllegalStateException("storage unavailable");
                    }
            );
        } finally {
            assertEquals("", data.getPersonalDateSlots().get(0).getLastClaimedOccurrenceKey());
        }
    }

    private static PlayerSignInData playerData() {
        PlayerSignInData data = new PlayerSignInData();
        data.setPersonalDateSlots(Collections.singletonList(
                new PlayerPersonalDateSlot("server_day", 0, CalendarIds.GREGORIAN,
                        8, 15, "")
        ));
        return data;
    }

    @Test
    public void reservingDoesNotAdvanceCursorAndDeduplicatesPersistedPendingOccurrences() {
        PlayerSignInData data = playerData();
        PersonalDatePreset preset = preset(PersonalDateDeliveryMode.SIGN_IN);
        java.util.List<xin.vanilla.sakura.data.personaldate.PersonalDateRewardSnapshot> reserved =
                service.reserveForSignIn(LocalDate.of(2026, 8, 15), Collections.singletonList(preset), data);
        assertEquals(1, reserved.size());
        assertEquals("", data.getPersonalDateSlots().get(0).getLastClaimedOccurrenceKey());
        xin.vanilla.sakura.data.SignInRecord record = new xin.vanilla.sakura.data.SignInRecord();
        record.setPersonalDateRewards(reserved);
        data.getSignInRecords().add(record);
        PlayerSignInData restored = new PlayerSignInData();
        restored.deserializeNBT(data.serializeNBT());
        assertEquals(0, service.reserveForSignIn(LocalDate.of(2026, 8, 15),
                Collections.singletonList(preset), restored).size());
        restored.getSignInRecords().get(0).setRewarded(true);
        assertEquals(1, service.reserveForSignIn(LocalDate.of(2026, 8, 15),
                Collections.singletonList(preset), restored).size());
    }

    @Test
    public void claimingSnapshotWithoutCurrentSlotCreatesInertCursorAndNeverRewindsNewerClaim() {
        PlayerSignInData data = playerData();
        xin.vanilla.sakura.data.personaldate.PersonalDateRewardSnapshot snapshot = service.reserveForSignIn(
                LocalDate.of(2026, 8, 15), Collections.singletonList(preset(PersonalDateDeliveryMode.SIGN_IN)), data).get(0);
        data.setPersonalDateSlots(Collections.emptyList());
        PersonalDateRewardService.completeClaim(data, snapshot);
        assertEquals("YEARLY:2026", data.getPersonalDateSlots().get(0).getLastClaimedOccurrenceKey());
        assertEquals(0, data.getPersonalDateSlots().get(0).getMonth());
        data.getPersonalDateSlots().get(0).setLastClaimedOccurrenceKey("YEARLY:2027");
        PersonalDateRewardService.completeClaim(data, snapshot);
        assertEquals("YEARLY:2027", data.getPersonalDateSlots().get(0).getLastClaimedOccurrenceKey());
    }

    private static PersonalDatePreset preset(PersonalDateDeliveryMode mode) {
        JsonObject content = new JsonObject();
        content.addProperty("text", "reward");
        Reward reward = new Reward(content, SakuraRewardTypes.MESSAGE, BigDecimal.ONE);
        return new PersonalDatePreset(
                "server_day", "Server Day", PersonalDateRecurrence.YEARLY,
                Collections.singletonList(CalendarIds.GREGORIAN), 1, mode,
                0, 0, new RewardList(Collections.singletonList(reward))
        );
    }

    @Test
    public void claimingOldYearlySnapshotCannotEraseNewMonthlyClaim() {
        PlayerSignInData data = playerData();
        PersonalDatePreset preset = preset(PersonalDateDeliveryMode.SIGN_IN);
        xin.vanilla.sakura.data.personaldate.PersonalDateRewardSnapshot snapshot = service.reserveForSignIn(
                LocalDate.of(2026, 8, 15), Collections.singletonList(preset), data).get(0);
        xin.vanilla.sakura.data.SignInRecord record = new xin.vanilla.sakura.data.SignInRecord();
        record.setPersonalDateRewards(Collections.singletonList(snapshot));
        data.getSignInRecords().add(record);
        preset.setRecurrence(PersonalDateRecurrence.MONTHLY);
        preset.setDeliveryMode(PersonalDateDeliveryMode.ONLINE);
        data.getPersonalDateSlots().get(0).setMonth(0);
        AtomicInteger grants = new AtomicInteger();
        service.deliver(PersonalDateDeliveryMode.ONLINE, LocalDate.of(2026, 8, 15),
                Collections.singletonList(preset), data, (reward, occurrence) -> grants.incrementAndGet());
        assertEquals(1, grants.get());
        PersonalDateRewardService.completeClaim(data, snapshot);
        record.setRewarded(true);
        PlayerSignInData reloaded = new PlayerSignInData();
        reloaded.deserializeNBT(data.serializeNBT());
        service.deliver(PersonalDateDeliveryMode.ONLINE, LocalDate.of(2026, 8, 15),
                Collections.singletonList(preset), reloaded, (reward, occurrence) -> grants.incrementAndGet());
        assertEquals(1, grants.get());
    }

    @Test
    public void lunarReservationDeduplicatesTwoTargetsInSameGregorianMonthlyPeriod() {
        PlayerSignInData data = playerData();
        PersonalDatePreset preset = preset(PersonalDateDeliveryMode.SIGN_IN);
        preset.setRecurrence(PersonalDateRecurrence.MONTHLY);
        preset.setCalendarIds(Collections.singletonList(CalendarIds.CHINESE_LUNAR));
        preset.setValidBeforeDays(15);
        preset.setValidAfterDays(15);
        data.getPersonalDateSlots().get(0).setCalendarId(CalendarIds.CHINESE_LUNAR);
        data.getPersonalDateSlots().get(0).setMonth(0);
        data.getPersonalDateSlots().get(0).setDay(13);
        assertEquals(1, service.reserveForSignIn(LocalDate.of(2026, 3, 16),
                Collections.singletonList(preset), data).size());
    }
}
