package com.mediwise.appointment.event;

import com.mediwise.appointment.model.Appointment;

public class AppointmentNoShowEvent extends AppointmentDomainEvent {

    public AppointmentNoShowEvent(Object source, Appointment appointment) {
        super(source, appointment);
    }

    @Override
    public String getEventType() {
        return "APPOINTMENT_NO_SHOW";
    }
}
