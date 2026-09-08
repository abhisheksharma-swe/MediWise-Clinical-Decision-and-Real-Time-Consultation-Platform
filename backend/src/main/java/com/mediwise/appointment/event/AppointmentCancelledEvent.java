package com.mediwise.appointment.event;

import com.mediwise.appointment.model.Appointment;
import lombok.Getter;

import java.util.UUID;

@Getter
public class AppointmentCancelledEvent extends AppointmentDomainEvent {
    private final UUID cancelledBy;

    public AppointmentCancelledEvent(Object source, Appointment appointment, UUID cancelledBy) {
        super(source, appointment);
        this.cancelledBy = cancelledBy;
    }

    @Override
    public String getEventType() {
        return "APPOINTMENT_CANCELLED";
    }
}
