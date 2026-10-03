import { HashRouter, Navigate, Route, Routes } from 'react-router';
import { Shell } from './Shell';
import { HomeScreen } from '../screens/Home';
import { MonthIndex, MonthScreen } from '../screens/Month';
import { YearScreen } from '../screens/Year';
import { NetWorthScreen } from '../screens/NetWorth';
import { SettingsScreen } from '../screens/Settings';

export function AppRoutes() {
  return (
    <HashRouter>
      <Routes>
        <Route element={<Shell />}>
          <Route index element={<HomeScreen />} />
          <Route path="month" element={<MonthIndex />} />
          <Route path="month/:ym" element={<MonthScreen tab="budget" />} />
          <Route path="month/:ym/tx" element={<MonthScreen tab="tx" />} />
          <Route path="month/:ym/c/:catId" element={<MonthScreen tab="budget" />} />
          <Route path="year" element={<YearScreen />} />
          <Route path="year/:year" element={<YearScreen />} />
          <Route path="networth" element={<NetWorthScreen />} />
          <Route path="settings" element={<SettingsScreen />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </HashRouter>
  );
}
