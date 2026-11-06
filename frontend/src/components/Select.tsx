import { useEffect, useId, useRef, useState, type AriaAttributes, type KeyboardEvent } from 'react';

type Option = { value: string; label: string };
interface Props extends AriaAttributes {
  id: string;
  value: string;
  options: Option[];
  onChange: (value: string) => void;
  className?: string;
}

/** Single selection with keyboard navigation; focus stays on the combobox. */
export function Select({ id, value, options, onChange, className = '', ...aria }: Props) {
  const listId = useId();
  const root = useRef<HTMLDivElement>(null);
  const button = useRef<HTMLButtonElement>(null);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const [above, setAbove] = useState(false);
  const search = useRef({ text: '', time: 0 });
  const selected = options.findIndex((option) => option.value === value);

  useEffect(() => {
    if (!open) return;
    const outside = (event: PointerEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener('pointerdown', outside);
    return () => document.removeEventListener('pointerdown', outside);
  }, [open]);

  useEffect(() => {
    if (open) document.getElementById(`${listId}-${active}`)?.scrollIntoView({ block: 'nearest' });
  }, [open, active, listId]);

  const show = (index = Math.max(0, selected)) => {
    const rect = button.current?.getBoundingClientRect();
    setAbove(Boolean(rect && window.innerHeight - rect.bottom < 260 && rect.top > window.innerHeight - rect.bottom));
    setActive(index);
    setOpen(true);
  };
  const choose = (index: number) => {
    const option = options[index];
    if (option) onChange(option.value);
    setOpen(false);
    button.current?.focus();
  };
  const onKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    if (event.key === 'Tab') { setOpen(false); return; }
    if (event.key === 'Escape') { event.preventDefault(); setOpen(false); return; }
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault();
      if (open) choose(active); else show();
      return;
    }
    if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
      event.preventDefault();
      const next = event.key === 'Home' ? 0 : event.key === 'End' ? options.length - 1
        : Math.max(0, Math.min(options.length - 1, active + (event.key === 'ArrowDown' ? 1 : -1)));
      if (open) setActive(next); else show(event.key === 'Home' || event.key === 'End' ? next : Math.max(0, selected));
    } else if (event.key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey) {
      const now = Date.now();
      const text = (now - search.current.time < 700 ? search.current.text : '') + event.key.toLocaleLowerCase();
      search.current = { text, time: now };
      const index = options.findIndex((option) => option.label.toLocaleLowerCase().startsWith(text));
      if (index >= 0) { event.preventDefault(); show(index); }
    }
  };

  return (
    <div ref={root} className={`select ${className}`} data-open={open} data-above={above}>
      <button ref={button} id={id} type="button" className="input select-trigger" role="combobox"
        aria-expanded={open} aria-controls={listId} aria-haspopup="listbox"
        aria-activedescendant={open ? `${listId}-${active}` : undefined}
        {...aria} onKeyDown={onKeyDown} onClick={() => open ? setOpen(false) : show()}
        onBlur={(event) => { if (!root.current?.contains(event.relatedTarget as Node)) setOpen(false); }}>
        <span>{options[selected]?.label ?? 'Выберите…'}</span>
        <svg width="18" height="18" viewBox="0 0 20 20" aria-hidden="true"><path d="m5 7 5 5 5-5" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></svg>
      </button>
      {open && <div id={listId} className="select-options" role="listbox" aria-labelledby={id}>
        {options.map((option, index) => <div key={option.value} id={`${listId}-${index}`} role="option"
          aria-selected={option.value === value} className={`select-option${active === index ? ' focused' : ''}`}
          onMouseDown={(event) => event.preventDefault()} onClick={() => choose(index)}>
          <span>{option.label}</span><span aria-hidden="true">{option.value === value ? '✓' : ''}</span>
        </div>)}
      </div>}
    </div>
  );
}
