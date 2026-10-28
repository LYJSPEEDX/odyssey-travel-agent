package com.example.odyssey;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** 使用行程自带的汇率进行换算；没有汇率时说明原因，不请求网络或伪造汇率。 */
final class CurrencyConverter {
    static void show(Context context, JSONObject money) {
        new Thread(() -> {
            String preferred = "";
            try { preferred = CurrencyPreference.preferred(new ProfileStore(context.getApplicationContext()).load()); }
            catch (Exception ignored) { /* 资料不可用仍可手选币种。 */ }
            String target = preferred;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (!(context instanceof Activity) || ((Activity) context).isFinishing() || ((Activity) context).isDestroyed()) return;
                open(context, money, target);
            });
        }, "currency-preference").start();
    }
    private static void open(Context context, JSONObject money, String preferred) {
        JSONArray rates = money.optJSONArray("exchangeRates");
        List<String> codes = new ArrayList<>();
        add(codes, preferred); add(codes, money.optString("displayCurrency", ""));
        if (rates != null) for (int i = 0; i < rates.length(); i++) {
            JSONObject rate = rates.optJSONObject(i); if (rate == null) continue;
            add(codes, rate.optString("baseCurrency", "")); add(codes, rate.optString("quoteCurrency", ""));
        }
        if (codes.isEmpty()) { new MaterialAlertDialogBuilder(context).setTitle("Currency converter")
                .setMessage("No currency is available. Select a preferred currency in your profile.").setPositiveButton("OK", null).show(); return; }
        // 可手动选择其他币种；没有对应汇率时明确提示不可换算。
        for (java.util.Currency currency : java.util.Currency.getAvailableCurrencies())
            if (currency.getDefaultFractionDigits() >= 0) add(codes, currency.getCurrencyCode());
        Collections.sort(codes);
        LinearLayout body = new LinearLayout(context); body.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(24 * context.getResources().getDisplayMetrics().density); body.setPadding(pad, 0, pad, 0);
        EditText amount = new EditText(context); amount.setSingleLine(true); amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setHint("Amount"); amount.setContentDescription("Amount to convert"); body.addView(amount);
        Spinner from = spinner(context, body, codes, "From currency"), to = spinner(context, body, codes, "To currency");
        int displayIndex = codes.indexOf(money.optString("displayCurrency", ""));
        if (displayIndex >= 0) from.setSelection(displayIndex);
        if (rates != null && rates.length() > 0 && rates.optJSONObject(0) != null) {
            int index = codes.indexOf(rates.optJSONObject(0).optString("baseCurrency")); if (index >= 0) from.setSelection(index);
        }
        int targetIndex = codes.indexOf(preferred); if (targetIndex >= 0) to.setSelection(targetIndex);
        TextView result = new TextView(context); result.setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_primary));
        result.setPadding(0, pad / 2, 0, pad / 2); result.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE); body.addView(result);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle("Currency converter").setView(body).setPositiveButton("Convert", null).setNegativeButton("Close", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                BigDecimal value = new BigDecimal(InputValidation.requiredText(amount.getText().toString(), 24, "Amount"));
                String base = from.getSelectedItem().toString(), quote = to.getSelectedItem().toString();
                if (base.equals(quote)) { result.setText(ExchangeMath.convert(value, BigDecimal.ONE, quote, false).toPlainString() + " " + quote + " · Same currency"); return; }
                if (rates != null) for (int i = 0; i < rates.length(); i++) {
                    JSONObject rate = rates.optJSONObject(i); if (rate == null) continue;
                    String b = rate.optString("baseCurrency"), q = rate.optString("quoteCurrency");
                    boolean inverse = b.equals(quote) && q.equals(base);
                    if (!(b.equals(base) && q.equals(quote)) && !inverse) continue;
                    BigDecimal factor = new BigDecimal(rate.optString("quotePerBase", ""));
                    JSONObject evidence = rate.optJSONObject("evidence");
                    String state = evidence == null ? "UNKNOWN" : evidence.optString("status", "UNKNOWN");
                    String date = rate.isNull("asOf") ? "Date unavailable" : rate.optString("asOf", "Date unavailable");
                    String label = "MOCK".equals(state) ? "Demo rate · Not current" : state + " · " + date;
                    result.setText(ExchangeMath.convert(value, factor, quote, inverse).toPlainString() + " " + quote + "\n" + label); return;
                }
                result.setText("No rate for this pair in the itinerary. Live conversion is not connected yet.");
            } catch (IllegalArgumentException invalid) { result.setText("Enter a valid non-negative amount."); }
        }));
        dialog.show();
    }
    private static void add(List<String> codes, String value) {
        try { String code = InputValidation.currencyCode(value); if (!code.isEmpty() && !codes.contains(code)) codes.add(code); }
        catch (IllegalArgumentException ignored) { }
    }
    private static Spinner spinner(Context context, LinearLayout body, List<String> values, String description) {
        Spinner spinner = new Spinner(context); spinner.setContentDescription(description);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter); body.addView(spinner); return spinner;
    }
    private CurrencyConverter() { }
}
