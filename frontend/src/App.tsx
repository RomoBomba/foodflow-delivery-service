import { Link, Route, Routes } from 'react-router-dom';
import { Layout } from './components/Layout';
import { RequireRole } from './components/RequireRole';
import { Empty } from './components/ui';
import { CatalogPage } from './pages/CatalogPage';
import { DishPage } from './pages/DishPage';
import { CartPage } from './pages/CartPage';
import { OrdersPage } from './pages/OrdersPage';
import { ReviewPage } from './pages/ReviewPage';
import { PreferencesPage } from './pages/PreferencesPage';
import { LoginPage, RegisterPage } from './pages/AuthPages';
import { KitchenApplyPage } from './pages/KitchenApplyPage';
import { KitchenOrdersPage } from './pages/kitchen/KitchenOrdersPage';
import { KitchenMenuPage } from './pages/kitchen/KitchenMenuPage';
import { KitchenReviewsPage } from './pages/kitchen/KitchenReviewsPage';
import { KitchenProfilePage } from './pages/kitchen/KitchenProfilePage';
import { AdminKitchensPage } from './pages/admin/AdminKitchensPage';
import { AdminReviewsPage } from './pages/admin/AdminReviewsPage';

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<CatalogPage />} />
        <Route path="dishes/:dishId" element={<DishPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />

        <Route path="cart" element={<RequireRole roles={['CLIENT']}><CartPage /></RequireRole>} />
        <Route path="orders" element={<RequireRole roles={['CLIENT']}><OrdersPage /></RequireRole>} />
        <Route path="orders/:orderId" element={<RequireRole roles={['CLIENT']}><OrdersPage /></RequireRole>} />
        <Route path="orders/:orderId/review" element={<RequireRole roles={['CLIENT']}><ReviewPage /></RequireRole>} />
        <Route path="preferences" element={<RequireRole roles={['CLIENT']}><PreferencesPage /></RequireRole>} />
        <Route path="kitchen/apply" element={<RequireRole roles={['CLIENT', 'KITCHEN']}><KitchenApplyPage /></RequireRole>} />

        <Route path="kitchen" element={<RequireRole roles={['KITCHEN']}><KitchenOrdersPage /></RequireRole>} />
        <Route path="kitchen/menu" element={<RequireRole roles={['KITCHEN']}><KitchenMenuPage /></RequireRole>} />
        <Route path="kitchen/reviews" element={<RequireRole roles={['KITCHEN']}><KitchenReviewsPage /></RequireRole>} />
        <Route path="kitchen/profile" element={<RequireRole roles={['KITCHEN']}><KitchenProfilePage /></RequireRole>} />

        <Route path="admin" element={<RequireRole roles={['ADMIN']}><AdminKitchensPage /></RequireRole>} />
        <Route path="admin/reviews" element={<RequireRole roles={['ADMIN']}><AdminReviewsPage /></RequireRole>} />

        <Route
          path="*"
          element={
            <Empty title="Страница не найдена">
              <p className="mt-16"><Link className="btn" to="/">В каталог</Link></p>
            </Empty>
          }
        />
      </Route>
    </Routes>
  );
}
