package microwavedram.lboatcam.compat;

import dev.isxander.debugify.api.DebugifyApi;

public class LBoatCamDebuggifyCompat implements DebugifyApi {
    @Override
    public String[] getDisabledFixes() {
        return new String[] {
                "MC-259512"
        };
    }
}
