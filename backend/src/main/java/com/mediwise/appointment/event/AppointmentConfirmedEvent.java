package com.mediwise.appointment.event;

import com.mediwise.appointment.model.Appointment;

public class AppointmentConfirmedEvent extends AppointmentDomainEvent {

    public AppointmentConfirmedEvent(Object source, Appointment appointment) {
        super(source, appointment);
    }

    @Override
    public String getEventType() {
        return "APPOINTMENT_CONFIRMED";
    }
}
