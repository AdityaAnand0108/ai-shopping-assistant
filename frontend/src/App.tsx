import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { CartProvider } from './cart/CartContext'
import { ChatProvider } from './chat/ChatContext'
import { Layout } from './components/Layout'
import { CartPage } from './pages/CartPage'
import { CatalogPage } from './pages/CatalogPage'
import { CheckoutPage } from './pages/CheckoutPage'
import { ChatPage } from './pages/ChatPage'
import { LandingPage } from './pages/LandingPage'
import { LoginPage } from './pages/LoginPage'
import { OrdersPage } from './pages/OrdersPage'
import { SignupPage } from './pages/SignupPage'
import { ThemeProvider } from './theme/ThemeContext'

export default function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <CartProvider>
          <ChatProvider>
            <BrowserRouter>
              <Routes>
                <Route element={<Layout />}>
                  {/* The landing page and the auth forms are the only pages a
                      signed-out visitor sees. */}
                  <Route index element={<LandingPage />} />
                  <Route path="login" element={<LoginPage />} />
                  <Route path="signup" element={<SignupPage />} />

                  {/* Gated in the UI only: /api/products is still public, so this
                      hides the catalog page rather than the catalog data. */}
                  <Route
                    path="catalog"
                    element={
                      <ProtectedRoute>
                        <CatalogPage />
                      </ProtectedRoute>
                    }
                  />
                  <Route
                    path="cart"
                    element={
                      <ProtectedRoute>
                        <CartPage />
                      </ProtectedRoute>
                    }
                  />
                  <Route
                    path="checkout"
                    element={
                      <ProtectedRoute>
                        <CheckoutPage />
                      </ProtectedRoute>
                    }
                  />
                  <Route
                    path="chat"
                    element={
                      <ProtectedRoute>
                        <ChatPage />
                      </ProtectedRoute>
                    }
                  />
                  <Route
                    path="orders"
                    element={
                      <ProtectedRoute>
                        <OrdersPage />
                      </ProtectedRoute>
                    }
                  />

                  <Route path="*" element={<Navigate to="/" replace />} />
                </Route>
              </Routes>
            </BrowserRouter>
          </ChatProvider>
        </CartProvider>
      </AuthProvider>
    </ThemeProvider>
  )
}
