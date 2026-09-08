package com.mediwise.appointment.event;

import com.mediwise.appointment.model.Appointment;

public class AppointmentStartedEvent extends AppointmentDomainEvent {

    public AppointmentStartedEvent(Object source, Appointment appointment) {
        super(source, appointment);
    }

    @Override
    public String getEventType() {
        return "APPOINTMENT_STARTED";
    }
}
