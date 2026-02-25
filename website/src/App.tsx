import { BrowserRouter, Routes, Route } from "react-router-dom";
import Layout from "@/components/Layout";
import Home from "@/pages/Home";
import DatToPng from "@/pages/DatToPng";
import CacheToPng from "@/pages/CacheToPng";
import MapHasher from "@/pages/MapHasher";

const App = () => (
  <BrowserRouter basename="/EvMod">
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Home />} />
        <Route path="DAT-to-PNG" element={<DatToPng />} />
        <Route path="CACHE-to-PNG" element={<CacheToPng />} />
        <Route path="MapHasher" element={<MapHasher />} />
      </Route>
    </Routes>
  </BrowserRouter>
);

export default App;
