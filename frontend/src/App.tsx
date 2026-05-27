import { Routes, Route, Navigate } from "react-router-dom";
import Login from "./pages/Login";
import CoursePicker from "./pages/CoursePicker";
import WeekPicker from "./pages/WeekPicker";
import FileList from "./pages/FileList";
import Settings from "./pages/Settings";
import UpcomingSubmissions from "./pages/UpcomingSubmissions";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/courses" replace />} />
      <Route path="/login" element={<Login />} />
      <Route path="/courses" element={<CoursePicker />} />
      <Route path="/courses/:courseId" element={<WeekPicker />} />
      <Route path="/courses/:courseId/week/:week" element={<FileList />} />
      <Route path="/courses/:courseId/bucket/:bucket" element={<FileList />} />
      <Route path="/courses/:courseId/upcoming" element={<UpcomingSubmissions />} />
      <Route path="/settings" element={<Settings />} />
    </Routes>
  );
}
