import { useEffect } from "react";
import { toast } from "react-toastify";
import { useAuth } from "../../context/AuthContext";

interface GroupNotification {
  type: "GROUP_EXPENSE_ADDED";
  groupId: number | string;
  groupName: string;
  title: string;
  amount: number;
  userShare: number;
  createdByEmail: string;
  message: string;
}

const getWebSocketUrl = (token: string) => {
  const protocol = window.location.protocol === "https:" ? "wss" : "ws";
  return `${protocol}://localhost:8080/ws/group-notifications?token=${encodeURIComponent(token)}`;
};

const GroupNotificationsListener = () => {
  const { isAuthenticated } = useAuth();

  useEffect(() => {
    if (!isAuthenticated) return;

    const token = localStorage.getItem("accessToken");
    if (!token) return;

    const jwtRegex = /^[A-Za-z0-9-_]+\.[A-Za-z0-9-_]+\.[A-Za-z0-9-_]+$/;
    if (!jwtRegex.test(token)) return;

    const socket = new WebSocket(getWebSocketUrl(token));

    socket.onmessage = (event) => {
      try {
        const notification = JSON.parse(event.data) as GroupNotification;
        if (notification.type === "GROUP_EXPENSE_ADDED") {
          toast.info(notification.message);
        }
      } catch {
        console.error("Nie udało się obsłużyć komunikatu grupowego.");
      }
    };

    socket.onerror = () => {
      console.error("Wystąpił błąd połączenia WebSocket z komunikatami grupowymi.");
    };

    return () => {
      socket.close();
    };
  }, [isAuthenticated]);

  return null;
};

export default GroupNotificationsListener;