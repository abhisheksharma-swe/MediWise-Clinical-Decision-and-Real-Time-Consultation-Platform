package com.mediwise.appointment.event;

import com.mediwise.appointment.model.Appointment;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Base type for appointment lifecycle events published after a state transition
 * commits. Listeners consume these via {@code @TransactionalEventListener} so
 * notifications/pushes never fire for a transition that later rolls back.
 *
 * Carries a unique {@code eventId} so downstream consumers (STOMP clients, FCM
 * receivers) can deduplicate if a message is delivered more than once.
 */
@Getter
public abstract class AppointmentDomainEvent extends ApplicationEvent {
    private final UUID eventId = UUID.randomUUID();
    private final Appointment appointment;

    protected AppointmentDomainEvent(Object source, Appointment appointment) {
        super(source);
        this.appointment = appointment;
    }

    public abstract String getEventType();

    public long getOccurredAt() {
        return getTimestamp();
    }
}
