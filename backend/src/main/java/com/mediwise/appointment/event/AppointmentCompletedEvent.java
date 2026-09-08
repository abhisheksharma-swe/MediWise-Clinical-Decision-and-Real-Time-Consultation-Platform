package com.mediwise.appointment.event;

import com.mediwise.appointment.model.Appointment;

public class AppointmentCompletedEvent extends AppointmentDomainEvent {

    public AppointmentCompletedEvent(Object source, Appointment appointment) {
        super(source, appointment);
    }

    @Override
    public String getEventType() {
        return "APPOINTMENT_COMPLETED";
    }
}
