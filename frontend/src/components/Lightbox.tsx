import { useEffect, useRef, useState } from "react";

const MIN_ZOOM = 0.5;
const MAX_ZOOM = 6;
const STEP = 1.2;

export function Lightbox({ src, onClose }: { src: string; onClose: () => void }) {
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [dragging, setDragging] = useState(false);

  const zoomRef = useRef(zoom);
  const panRef = useRef(pan);
  zoomRef.current = zoom;
  panRef.current = pan;

  const containerRef = useRef<HTMLDivElement>(null);
  const dragStart = useRef<{ x: number; y: number; panX: number; panY: number } | null>(null);

  const reset = () => { setZoom(1); setPan({ x: 0, y: 0 }); };
  const zoomIn = () => setZoom(z => Math.min(MAX_ZOOM, z * STEP));
  const zoomOut = () => setZoom(z => Math.max(MIN_ZOOM, z / STEP));

  useEffect(() => { reset(); }, [src]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
      else if (e.key === "+" || e.key === "=") zoomIn();
      else if (e.key === "-" || e.key === "_") zoomOut();
      else if (e.key === "0") reset();
    };
    document.addEventListener("keydown", onKey);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = prevOverflow;
    };
  }, [onClose]);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;

    const onWheel = (e: WheelEvent) => {
      e.preventDefault();

      const pixelDelta =
        e.deltaMode === 1 ? e.deltaY * 16 :
        e.deltaMode === 2 ? e.deltaY * 800 :
        e.deltaY;

      const coeff = e.ctrlKey ? 0.02 : 0.0015;
      const factor = Math.exp(-pixelDelta * coeff);

      const prevZoom = zoomRef.current;
      const nextZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, prevZoom * factor));
      if (nextZoom === prevZoom) return;

      const actual = nextZoom / prevZoom;
      const rect = el.getBoundingClientRect();
      const cx = e.clientX - rect.left - rect.width / 2;
      const cy = e.clientY - rect.top - rect.height / 2;
      const prevPan = panRef.current;

      setZoom(nextZoom);
      setPan({
        x: cx - (cx - prevPan.x) * actual,
        y: cy - (cy - prevPan.y) * actual,
      });
    };

    el.addEventListener("wheel", onWheel, { passive: false });
    return () => el.removeEventListener("wheel", onWheel);
  }, []);

  const onPointerDown = (e: React.PointerEvent<HTMLImageElement>) => {
    e.preventDefault();
    (e.target as HTMLElement).setPointerCapture(e.pointerId);
    dragStart.current = { x: e.clientX, y: e.clientY, panX: pan.x, panY: pan.y };
    setDragging(true);
  };

  const onPointerMove = (e: React.PointerEvent) => {
    if (!dragStart.current) return;
    setPan({
      x: dragStart.current.panX + (e.clientX - dragStart.current.x),
      y: dragStart.current.panY + (e.clientY - dragStart.current.y),
    });
  };

  const onPointerUp = () => {
    dragStart.current = null;
    setDragging(false);
  };

  return (
    <div ref={containerRef} className="lightbox" role="dialog" aria-modal="true" onClick={onClose}>
      <div className="lightbox-toolbar" onClick={(e) => e.stopPropagation()}>
        <button type="button" className="lightbox-btn" aria-label="Zoom out" onClick={zoomOut}>−</button>
        <button type="button" className="lightbox-btn lightbox-zoom-label" aria-label="Reset zoom"
                onClick={reset}>{Math.round(zoom * 100)}%</button>
        <button type="button" className="lightbox-btn" aria-label="Zoom in" onClick={zoomIn}>+</button>
      </div>
      <button type="button" className="lightbox-close" aria-label="Close" onClick={onClose}>×</button>
      <img
        src={src}
        alt=""
        className="lightbox-image"
        draggable={false}
        style={{
          transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})`,
          cursor: dragging ? "grabbing" : zoom > 1 ? "grab" : "zoom-in",
        }}
        onClick={(e) => e.stopPropagation()}
        onDoubleClick={(e) => { e.stopPropagation(); zoom === 1 ? setZoom(2) : reset(); }}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
      />
      <div className="lightbox-hint">scroll · pinch · drag · double-click · esc</div>
    </div>
  );
}
