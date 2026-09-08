package com.mediwise.appointment.event;

import com.mediwise.appointment.model.Appointment;

public class AppointmentBookedEvent extends AppointmentDomainEvent {

    public AppointmentBookedEvent(Object source, Appointment appointment) {
        super(source, appointment);
    }

    @Override
    public String getEventType() {
        return "APPOINTMENT_BOOKED";
    }
}
