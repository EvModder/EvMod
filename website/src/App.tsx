import { HashRouter, Routes, Route } from "react-router-dom";
import Layout from "@/components/Layout";
import Home from "@/pages/Home";
import DatToPng from "@/pages/DatToPng";
import CacheToPng from "@/pages/CacheToPng";
import MapHasher from "@/pages/MapHasher";

const App = () => (
  <HashRouter>
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Home />} />
        <Route path="DAT-to-PNG" element={<DatToPng />} />
        <Route path="CACHE-to-PNG" element={<CacheToPng />} />
        <Route path="MapHasher" element={<MapHasher />} />
      </Route>
    </Routes>
  </HashRouter>
);

export default App;
