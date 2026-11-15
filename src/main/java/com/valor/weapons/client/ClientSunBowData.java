package com.valor.weapons.client;

/** Client-side copy of the Sun's Bow state, for the HUD and the camera lock. Contains no Minecraft client classes. */
public final class ClientSunBowData {
    public static int stacks = 0;

    /** True while the Infinite Arrow burst is firing: the camera is frozen at lockYaw / lockPitch. */
    public static boolean lockActive = false;
    public static float lockYaw = 0.0F;
    public static float lockPitch = 0.0F;

    private ClientSunBowData() {}
}
