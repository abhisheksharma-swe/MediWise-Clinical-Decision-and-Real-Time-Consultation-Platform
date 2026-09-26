package com.mediwise.appointment.event;

import com.mediwise.appointment.model.Appointment;
import lombok.Getter;

import java.util.UUID;

@Getter
public class AppointmentRescheduledEvent extends AppointmentDomainEvent {
    private final UUID originalSlotId;

    public AppointmentRescheduledEvent(Object source, Appointment appointment, UUID originalSlotId) {
        super(source, appointment);
        this.originalSlotId = originalSlotId;
    }

    @Override
    public String getEventType() {
        return "APPOINTMENT_RESCHEDULED";
    }
}
