# MediWise

MediWise is an Android healthcare app I built using **Kotlin and Jetpack Compose**.

The main focus of the app is connecting patients with doctors — from finding a doctor and booking an appointment to chatting during a consultation and managing medical records. I also integrated an AI-based symptom analysis flow for the initial assessment.

## What it does

* Patient and doctor accounts
* Doctor search and appointment booking
* Doctor availability and time slots
* Real-time chat using WebSockets
* Video consultation support
* AI-based symptom analysis
* Medical records and file uploads
* Razorpay payment integration
* Role-based authentication

## Screenshots
<div align="center">

<table>
  <tr>
    <td align="center" style="padding: 10px;">
      <img src="https://github.com/user-attachments/assets/d4c5a301-7bc9-481e-a19f-516df95c45cb" width="220">
    </td>
    <td align="center" style="padding: 10px;">
      <img src="https://github.com/user-attachments/assets/62e8c8bf-9276-407b-843f-7c13e2242020" width="220">
    </td>
    <td align="center" style="padding: 10px;">
      <img src="https://github.com/user-attachments/assets/c9dc35c1-3940-48a9-ae16-5d189d3d99ca" width="220">
    </td>
  </tr>
</table>

<br><br>

<img src="https://github.com/user-attachments/assets/60b14de0-749f-4753-8ed8-e67e14656376" width="750">

</div>



## Android

The Android app is built with:

* Kotlin
* Jetpack Compose
* MVVM
* Coroutines + Flow
* Hilt
* Retrofit / OkHttp
* DataStore
* Firebase Auth

The app follows a fairly standard Android architecture with separate UI, ViewModel, repository, and data/API layers.

## Backend

The app uses a **Spring Boot** backend for APIs, authentication, appointments, chat, and other server-side operations.

PostgreSQL, MongoDB and Redis are used depending on the type of data. AWS S3 is used for medical files.

## Admin Dashboard

A React (Vite) admin panel for platform management — user/doctor verification, appointment oversight, analytics, and audit logs. Talks to the same Spring Boot backend.

## Structure

```text
MediWise/
├── android-app/
├── backend/
└── mediwise-admin/
```

## Run locally

Clone the repository:

```bash
git clone https://github.com/abhisheksharma-swe/MediWise-Clinical-Decision-and-Real-Time-Consultation-Platform.git
```

Open `android-app` in Android Studio and configure the backend URL and Firebase settings.

For the backend:

```bash
cd backend
docker compose up --build
```

API runs on:

```text
http://localhost:8080
```

For the admin dashboard:

```bash
cd mediwise-admin
npm install
npm run dev
```

## Why I built it

MediWise was developed as an industry-oriented **Android healthcare application**, focusing on practical Android architecture, scalable API integration, secure authentication, real-time communication, appointment workflows, payments, and AI-assisted features.

The project follows patterns and technologies commonly used in modern production Android applications, with the Android client integrated with a Spring Boot backend and supporting cloud services.

## License

This project is licensed under the [MIT License](LICENSE).
