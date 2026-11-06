import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { Alert } from './ui';

type Tone = 'ok' | 'info' | 'warn' | 'danger';
interface Toast {
  id: number;
  tone: Tone;
  text: string;
}

const ToastContext = createContext<((text: string, tone?: Tone) => void) | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const push = useCallback((text: string, tone: Tone = 'ok') => {
    const id = Date.now() + Math.random();
    setToasts((list) => [...list.slice(-2), { id, tone, text }]);
    window.setTimeout(() => setToasts((list) => list.filter((t) => t.id !== id)), tone === 'danger' ? 8000 : 5000);
  }, []);

  const value = useMemo(() => push, [push]);
  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toast-zone" aria-live="polite">
        {toasts.map((toast) => (
          <Alert
            key={toast.id}
            tone={toast.tone}
            action={
              <button
                type="button"
                className="icon-btn"
                aria-label="Закрыть уведомление"
                onClick={() => setToasts((list) => list.filter((t) => t.id !== toast.id))}
              >
                ✕
              </button>
            }
          >
            {toast.text}
          </Alert>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) throw new Error('useToast вне ToastProvider');
  return context;
}
