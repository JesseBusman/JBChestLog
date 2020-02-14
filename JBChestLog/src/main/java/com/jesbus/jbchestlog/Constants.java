package com.jesbus.jbchestlog;

import java.util.UUID;

class Constants
{
	public static final UUID EXPLOSION_UUID = new UUID(0x544E542D544E5421L, 0x564552592D534144L);
	
	public static final UUID HOPPER_BELOW_THIS_CONTAINER_UUID = new UUID(0xb06f70ab47bb4161L, 0xdc3f630d3192b90dL);
	public static final UUID HOPPER_MINECART_BELOW_THIS_CONTAINER_UUID = new UUID(0xdc3f630d3112b90dL, 0xb06f70ab47bb4181L);
	public static final UUID CONTAINER_BELOW_THIS_HOPPER_UUID = new UUID(0xdc3f630b47ab4161L, 0xb06f7bad3112b90dL);
	public static final UUID CONTAINER_BELOW_THIS_HOPPER_MINECART_UUID = new UUID(0xdc3f630b47bb4161L, 0xb06f74ad3112b90dL);

	public static final UUID CONTAINER_ABOVE_THIS_HOPPER_UUID = new UUID(0xb16f70ab47bb4161L, 0xdc3f630d3c12b90dL);
	public static final UUID CONTAINER_ABOVE_THIS_HOPPER_MINECART_UUID = new UUID(0xdd3f630d3112b90dL, 0xb06f70ab47bb41a1L);
	public static final UUID HOPPER_ABOVE_THIS_CONTAINER_UUID = new UUID(0xdd3f630b49bb4161L, 0xb06f70ad3152b90dL);
	public static final UUID HOPPER_MINECART_ABOVE_THIS_CONTAINER_UUID = new UUID(0xdd3f630b47bb4161L, 0xb06f70ad3112b90dL);

	public static final UUID FURNACE_SMELTED_UUID = new UUID(0x16c9b8af414eb968L, 0x817a1c59bbc1581aL);
	public static final UUID FURNACE_BURNED_UUID = new UUID(0x7779b8af414eb968L, 0x817a1c58903581aL);
	public static final UUID BREWING_STAND_CONSUMED_UUID = new UUID(0x8889b8af414eb968L, 0x817a1c59bb3211aL);
	public static final UUID BREWING_STAND_BREWED_UUID = new UUID(0x777888af414eb968L, 0x817a1123903581aL);

	public static final long SECONDS_BETWEEN_SAVING_ALL_CHANGES_TO_DISK = 15 * 60;

	public static final UUID DROPPED_ITEM_UUID = new UUID(0x630b47bb4161dc3fL, 0xb031126f70adb90dL);

    public final static long MAX_SECONDS_DIFFERENCE_TO_MERGE_DIFFS = 30 * 60;
    public final static long SECONDS_BEFORE_CONTAINER_EVICTED_FROM_CACHE = 45 * 60;

	public final static long SIMPLIFIER_VIEW_RANGE = 16;
    
    public final static long MAGIC_DELIMITER = 0x7399f48030aca50dL;

	public final static boolean DEBUGGING = false;

	public final static long MAX_AMOUNT_OF_DIFFS_IN_ONE_CHAT_LINE = 1;

	public final static boolean WHEN_PLAYER_BREAKS_CONTAINER_RECORD_THEM_AS_TAKING_THE_ITEMS = false;

	public final static long MAX_MS_BETWEEN_CLICKS_TO_SHOW_NEXT_LOG_PAGE = 750;

	public final static long SECONDS_BEFORE_CLEAR_COMMAND_EXPIRES = 5;


	public final static String PERMISSION_VIEW_OWN = "JBChestLog.view.own";
	public final static String PERMISSION_VIEW_ANY = "JBChestLog.view.any";

	public final static String PERMISSION_CLEAR_OWN = "JBChestLog.clear.own";
	public final static String PERMISSION_CLEAR_ANY = "JBChestLog.clear.any";
	public final static String PERMISSION_CLEAR_ALL = "JBChestLog.clear.all";


}
