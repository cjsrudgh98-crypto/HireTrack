import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';

const ToastContext = createContext<(message: string) => void>(() => {});

export function ToastProvider({ children }: { children: ReactNode }) {
  const [messages, setMessages] = useState<{ id: number; text: string }[]>([]);

  const show = useCallback((text: string) => {
    const id = Date.now() + Math.random();
    setMessages((prev) => [...prev, { id, text }]);
    setTimeout(() => setMessages((prev) => prev.filter((m) => m.id !== id)), 3500);
  }, []);

  return (
    <ToastContext.Provider value={show}>
      {children}
      <div className="toast-stack" role="status" aria-live="polite">
        {messages.map((m) => (
          <div key={m.id} className="toast">
            {m.text}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export const useToast = () => useContext(ToastContext);
