package com.openlab.mx.movo.payment.gateway;

import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.checkout.Session;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Transfer;
import com.stripe.net.RequestOptions;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.PaymentIntentCaptureParams;
import com.stripe.param.TransferCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Adapter Stripe (spec §2/§8): Checkout Sessions con manual capture.
 * Única clase que importa com.stripe.*; monta URLs desde app.base-url y
 * manda montos en centavos (× 100). Nunca procesa ni loguea PAN/CVC.
 */
public class StripePaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(StripePaymentGateway.class);

    private final String secretKey;
    private final String baseUrl;

    StripePaymentGateway(String secretKey, String baseUrl) {
        this.secretKey = secretKey;
        this.baseUrl = baseUrl;
    }

    /** Los montos viajan en centavos: 380.00 → 38000. */
    static long toCents(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("monto nulo");
        }
        return amount.movePointRight(2).longValueExact();
    }

    @Override
    public GatewaySession createCheckout(GatewayCheckoutRequest q) {
        SessionCreateParams.LineItem.PriceData price =
                SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(q.currency().toLowerCase())
                        .setUnitAmount(toCents(q.unitAmount()))
                        .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                .setName(q.description()).build())
                        .build();
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(baseUrl + "/pago/exito?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(baseUrl + "/experiences/" + q.metadata().get("experienceId")
                        + "?instanceId=" + q.metadata().get("instanceId")
                        + "&attendees=" + q.metadata().get("attendees")
                        + "#fechas")
                .putAllMetadata(q.metadata())
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                        .setCaptureMethod(SessionCreateParams.PaymentIntentData.CaptureMethod.MANUAL)
                        .putAllMetadata(q.metadata())
                        .build())
                .addLineItem(SessionCreateParams.LineItem.builder()
                                .setQuantity((long) q.quantity())
                                .setPriceData(price)
                                .build())
                .build();
        try {
            Session session = Session.create(params, opciones(null));
            log.info("Checkout session creado en Stripe (cs parciales no se loguean completos)");
            // El PaymentIntent nace al completar el pago: aquí aún no existe.
            return new GatewaySession(session.getId(), session.getUrl(), null, "open");
        } catch (StripeException e) {
            throw new IllegalStateException("No se pudo crear la sesión de pago en Stripe", e);
        }
    }

    @Override
    public GatewaySession authorizeInfo(String sessionId) {
        try {
            Session session = Session.retrieve(sessionId, opciones(null));
            String status = ("complete".equals(session.getStatus())
                    && "paid".equals(session.getPaymentStatus())) ? "paid" : "unpaid";
            return new GatewaySession(session.getId(), session.getUrl(),
                    session.getPaymentIntent(), status);
        } catch (StripeException e) {
            throw new IllegalStateException("No se pudo revalidar la sesión en Stripe", e);
        }
    }

    @Override
    public void capture(String paymentIntentId) {
        try {
            RequestOptions opciones = opciones(paymentIntentId + ":capture");
            PaymentIntent pi = PaymentIntent.retrieve(paymentIntentId, opciones);
            pi.capture(opciones);
        } catch (StripeException e) {
            throw new IllegalStateException("El pago no se capturó en Stripe", e);
        }
    }

    @Override
    public void capturePartial(String paymentIntentId, BigDecimal amount) {
        try {
            RequestOptions opciones = opciones(paymentIntentId + ":capturePartial");
            PaymentIntent pi = PaymentIntent.retrieve(paymentIntentId, opciones);
            pi.capture(PaymentIntentCaptureParams.builder()
                    .setAmountToCapture(toCents(amount)).build(), opciones);
        } catch (StripeException e) {
            throw new IllegalStateException("El pago no se capturó (parcial) en Stripe", e);
        }
    }

    @Override
    public void cancel(String paymentIntentId) {
        try {
            RequestOptions options = opciones(paymentIntentId + ":cancel");
            PaymentIntent pi = PaymentIntent.retrieve(paymentIntentId, options);
            pi.cancel(options);
        } catch (StripeException e) {
            throw new IllegalStateException("El pago no se canceló en Stripe", e);
        }
    }

    // ─── Stripe Connect ───────────────────────────────────────────────────

    @Override
    public String createConnectAccount(String country, String email) {
        try {
            Account account = Account.create(AccountCreateParams.builder()
                    .setType(AccountCreateParams.Type.EXPRESS)
                    .setCountry(country)
                    .setEmail(email)
                    .setCapabilities(AccountCreateParams.Capabilities.builder()
                            .setTransfers(AccountCreateParams.Capabilities.Transfers.builder()
                                    .setRequested(true)
                                    .build())
                            .build())
                    .build(), opciones(null));
            log.info("Cuenta Connect creada: {}", account.getId());
            return account.getId();
        } catch (StripeException e) {
            throw new IllegalStateException("No se pudo crear la cuenta Connect en Stripe", e);
        }
    }

    @Override
    public String createAccountLink(String stripeAccountId, String refreshUrl, String returnUrl) {
        try {
            AccountLink link = AccountLink.create(AccountLinkCreateParams.builder()
                    .setAccount(stripeAccountId)
                    .setRefreshUrl(refreshUrl)
                    .setReturnUrl(returnUrl)
                    .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                    .build(), opciones(null));
            return link.getUrl();
        } catch (StripeException e) {
            throw new IllegalStateException("No se pudo generar el enlace de onboarding", e);
        }
    }

    @Override
    public ConnectAccountInfo retrieveConnectAccount(String stripeAccountId) {
        try {
            Account account = Account.retrieve(stripeAccountId, opciones(null));
            return new ConnectAccountInfo(
                    account.getId(),
                    Boolean.TRUE.equals(account.getChargesEnabled()),
                    Boolean.TRUE.equals(account.getPayoutsEnabled()),
                    account.getRequirements() != null ? account.getRequirements().getDisabledReason() : null
            );
        } catch (StripeException e) {
            throw new IllegalStateException("No se pudo consultar la cuenta Connect en Stripe", e);
        }
    }

    @Override
    public String transferToConnectedAccount(String stripeAccountId, BigDecimal amount, String currency, String idempotencyKey) {
        try {
            Transfer transfer = Transfer.create(TransferCreateParams.builder()
                    .setAmount(toCents(amount))
                    .setCurrency(currency.toLowerCase())
                    .setDestination(stripeAccountId)
                    .build(), opciones(idempotencyKey));
            log.info("Transfer creado: {} a {}", transfer.getId(), stripeAccountId);
            return transfer.getId();
        } catch (StripeException e) {
            throw new IllegalStateException("No se pudo ejecutar el transfer a la cuenta conectada", e);
        }
    }

    private RequestOptions opciones(String claveIdempotencia) {
        var builder = RequestOptions.builder().setApiKey(secretKey);
        if (claveIdempotencia != null) {
            builder.setIdempotencyKey(claveIdempotencia);
        }
        return builder.build();
    }
}
