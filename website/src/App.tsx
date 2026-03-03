import { BrowserRouter, Routes, Route } from "react-router-dom";
import type { ReactElement } from "react";
import Layout from "@/components/Layout";
import Home from "@/pages/Home";
import PngToDat from "@/pages/PngToDat";
import DatToPng from "@/pages/DatToPng";
import NbtToPng from "@/pages/NbtToPng";
import CacheToPng from "@/pages/CacheToPng";
import MapHasher from "@/pages/MapHasher";
import { TOOL_PAGES, type ToolPageId } from "@/lib/toolPages";

const routeElements: Record<ToolPageId, ReactElement> = {
  pngToDat: <PngToDat />,
  datToPng: <DatToPng />,
  nbtToPng: <NbtToPng />,
  cacheToPng: <CacheToPng />,
  mapHasher: <MapHasher />,
};

const basename = import.meta.env.BASE_URL === "/" ? undefined : import.meta.env.BASE_URL.replace(/\/$/, "");

const App = () => (
  <BrowserRouter basename={basename}>
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Home />} />
        {TOOL_PAGES.map(({ id, path }) => (
          <Route key={id} path={path.slice(1)} element={routeElements[id]} />
        ))}
      </Route>
    </Routes>
  </BrowserRouter>
);

export default App;
