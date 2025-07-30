package com.example.meterkenshin.dlms;

import static java.lang.String.format;

import android.content.Context;
import android.icu.util.Calendar;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class DLMS {
    public final static int RANK_SUPER = 0;
    public final static int RANK_ADMIN = 1;
    public final static int RANK_POWER = 2;
    public final static int RANK_READER = 3;
    public final static int RANK_PUBLIC = 4;
    public final static int IST_FIRM_VER = 1;
    public final static int IST_FIRM_SIG = 2;
    public final static int IST_TIME_NOW = 3;
    public final static int IST_DATE_NOW = 4;
    public final static int IST_LOGICAL_NAME = 5;
    public final static int IST_APPROVAL_NO = 6;
    public final static int IST_SERIAL_NO = 7;
    public final static int IST_CONTRUCT_NO = 8;
    public final static int IST_BLUETOOTH_ID = 9;
    public final static int IST_PROFILE_STATUS = 10;
    public final static int IST_EVENT = 11;
    public final static int IST_LOG_CODE = 12;
    public final static int IST_FAULT = 13;
    public final static int IST_ALARM_REG1 = 14;
    public final static int IST_ALARM_REG2 = 15;
    public final static int IST_ALARM_FIL1 = 16;
    public final static int IST_ALARM_FIL2 = 17;
    public final static int IST_ALARM_DSC1 = 18;
    public final static int IST_ALARM_DSC2 = 19;
    public final static int IST_RAM = 20;
    public final static int IST_E2P = 21;
    public final static int IST_ROM = 22;
    public final static int IST_CAL_ENERGY = 23;
    public final static int IST_ENERGY = 24;
    public final static int IST_CAL_VOLTAMP = 25;
    public final static int IST_VOLTAMP = 26;
    public final static int IST_CALB_RTC0 = 27;
    public final static int IST_SETTING0 = 28;
    public final static int IST_OBJECT_MAP0 = 29;
    public final static int IST_CLIENT0 = 30;
    public final static int IST_ACCESS_TBL0 = 31;
    public final static int IST_DISPLAY0 = 32;
    public final static int IST_UNIT0 = 33;
    public final static int IST_RECORD0 = 34;
    public final static int IST_SETUP_PULS = 35;
    public final static int IST_DETECT = 36;
    public final static int IST_TYPE = 37;
    public final static int IST_MODEL = 38;
    public final static int IST_BATT_VOLT = 39;
    public final static int IST_CPUTIME = 40;
    public final static int IST_MAGNET = 41;
    public final static int IST_CPUTEMP = 42;
    public final static int IST_FWD_POWER = 43;
    public final static int IST_FWD_ENERGY = 44;
    public final static int IST_FWD_DEMAND = 45;
    public final static int IST_BAK_POWER = 46;
    public final static int IST_BAK_ENERGY = 47;
    public final static int IST_BAK_DEMAND = 48;
    public final static int IST_REACTIVE_L = 49;
    public final static int IST_REACTIVE_C = 50;
    public final static int IST_AMPR0 = 51;
    public final static int IST_MINIMUM_VOLT0 = 52;
    public final static int IST_AVERAGE_VOLT0 = 53;
    public final static int IST_VOLT0 = 54;
    public final static int IST_AVERAGE_VOLT0_15 = 55;
    public final static int IST_POWER_FACTOR = 56;
    public final static int IST_FREQ = 57;
    public final static int IST_ABS_ENERGY = 58;
    public final static int IST_NET_ENERGY = 59;
    public final static int IST_AMPR1 = 60;
    public final static int IST_AMPR1_30 = 61;
    public final static int IST_MINIMUM_VOLT1 = 62;
    public final static int IST_AVERAGE_VOLT1 = 63;
    public final static int IST_VOLT1 = 64;
    public final static int IST_AVERAGE_VOLT1_15 = 65;
    public final static int IST_AMPR2 = 66;
    public final static int IST_MINIMUM_VOLT2 = 67;
    public final static int IST_AVERAGE_VOLT2 = 68;
    public final static int IST_VOLT2 = 69;
    public final static int IST_AMPR3 = 70;
    public final static int IST_MINIMUM_VOLT3 = 71;
    public final static int IST_VOLT3 = 72;
    public final static int IST_COMB_POWER = 73;
    public final static int IST_MAX_FWD_DEMAND = 74;
    public final static int IST_MAX_FWD_DEMAND0 = 75;
    public final static int IST_MAX_BAK_DEMAND = 76;
    public final static int IST_MAX_BAK_DEMAND0 = 77;
    public final static int IST_POWER_QUALITY = 78;
    public final static int IST_METER_LOG = 79;
    public final static int IST_BILLING_PARAMS = 80;
    public final static int IST_LOAD_PROFILE = 81;
    public final static int IST_REACTIVE_RECORD = 82;
    public final static int IST_AMPR_RECORD = 83;
    public final static int IST_SPECIFICATION = 84;
    public final static int IST_CHECK_MEASURE = 85;
    public final static int IST_DATETIME_NOW = 86;
    public final static int IST_DATETIME_RTC = 87;
    public final static int IST_DEMAND_RESET = 88;
    public final static int IST_ASSO_LN0 = 89;
    public final static int IST_ASSO_LN1 = 90;
    public final static int IST_ASSO_LN2 = 91;
    public final static int IST_ASSO_LN3 = 92;
    public final static int IST_IMG_TRANS = 93;
    public final static int IST_SETUP_OPTI = 94;
    public final static int IST_SETUP_HDLC0 = 95;
    public final static int IST_SETUP_HDLC1 = 96;
    public final static int IST_SECURITY_NONE = 97;
    public final static int IST_SECURITY_HLS = 98;
    public final static int IST_SECURITY_LLS = 99;
    private static final byte SECURITY_CONTROL_BYTE_ENCRYPTED_AUTHENTICATED = (byte) 0x30; // Example
    private final static byte[][] g_ist = {
            {(byte) 0, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00},/* 0: 0 */
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x01, (byte) 0xff},/* 1: Active firmware version */
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0x08, (byte) 0xff},/* 2: Active firmware signature */
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x09, (byte) 0x01, (byte) 0xff},/* 3: Time*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x09, (byte) 0x02, (byte) 0xff},/* 4: Date*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x2a, (byte) 0x00, (byte) 0x00, (byte) 0xff},/* 5: COSEM logical device name */
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x60, (byte) 0x01, (byte) 0x00, (byte) 0xff},/* 6: Approval NO(PEA NO.).*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x60, (byte) 0x01, (byte) 0x01, (byte) 0xff},/* 7: SerialID*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x60, (byte) 0x01, (byte) 0x02, (byte) 0xff},/* 8: Contract NO.*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x60, (byte) 0x01, (byte) 0x09, (byte) 0xff},/* 9: Bluetooth mac address*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x60, (byte) 0x0a, (byte) 0x01, (byte) 0xff},/* 10: Profile status */
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x60, (byte) 0x0b, (byte) 0x00, (byte) 0xff},/* 11: Power quality code*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x60, (byte) 0x0b, (byte) 0x0a, (byte) 0xff},/* 12: Meter log code*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x60, (byte) 0x0b, (byte) 0x0b, (byte) 0xff},/* 13: Fault code*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x61, (byte) 0x62, (byte) 0x00, (byte) 0xff},/* 14: Alarm register 1 */
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x61, (byte) 0x62, (byte) 0x01, (byte) 0xff},/* 15: Alarm register 2 */
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x61, (byte) 0x62, (byte) 0x0a, (byte) 0xff},/* 16: Alarm Filter 1 */
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x61, (byte) 0x62, (byte) 0x0b, (byte) 0xff},/* 17: Alarm Filter 2 */
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x61, (byte) 0x62, (byte) 0x14, (byte) 0xff},/* 18: Alarm Descriptor 1 */
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x61, (byte) 0x62, (byte) 0x15, (byte) 0xff},/* 19: Alarm Discriptor 2 */
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x80, (byte) 0x00, (byte) 0x00, (byte) 0xff},/* 20: RAM*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x80, (byte) 0x00, (byte) 0x01, (byte) 0xff},/* 21: EEPROM*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x80, (byte) 0x00, (byte) 0x02, (byte) 0xff},/* 22: ROM*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x81, (byte) 0x00, (byte) 0x00, (byte) 0xff},/* 23: Energy calibcration*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x81, (byte) 0x00, (byte) 0x01, (byte) 0xff},/* 24: Read energy(BF)　*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x82, (byte) 0x00, (byte) 0x00, (byte) 0xff},/* 25: Voltage and ampare calibration*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x82, (byte) 0x00, (byte) 0x01, (byte) 0xff},/* 26: 電圧/電流読み出し(D3)　*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x83, (byte) 0x00, (byte) 0x00, (byte) 0xff},/* 27: RTC calibration*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x90, (byte) 0x00, (byte) 0x00, (byte) 0xff},/* 28: Factory setting*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x91, (byte) 0x00, (byte) 0x00, (byte) 0xff},/* 29: Object map*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x91, (byte) 0x00, (byte) 0x01, (byte) 0xff},/* 30: Client setup*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x91, (byte) 0x00, (byte) 0x02, (byte) 0xff},/* 31: Access table setting*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x92, (byte) 0x00, (byte) 0x00, (byte) 0xff},/* 32: Display setting*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x93, (byte) 0x00, (byte) 0x00, (byte) 0xff},/* 33: Unit setting*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0x94, (byte) 0x00, (byte) 0x00, (byte) 0xff},/* 34: Record setting*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0xa0, (byte) 0x00, (byte) 0x00, (byte) 0xff},/* 35: Certification setting*/
            {(byte) 1, (byte) 0x00, (byte) 0x00, (byte) 0xa0, (byte) 0x00, (byte) 0x01, (byte) 0xff},/* 36: Detect event setting*/
            {(byte) 1, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xff},/* 37: Meter type*/
            {(byte) 1, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0xff},/* 38: Product model*/
            {(byte) 3, (byte) 0x00, (byte) 0x00, (byte) 0x60, (byte) 0x06, (byte) 0x03, (byte) 0xff},/* 39: Battery voltage*/
            {(byte) 3, (byte) 0x00, (byte) 0x00, (byte) 0x60, (byte) 0x08, (byte) 0x00, (byte) 0xff},/* 40: Operating time objects */
            {(byte) 3, (byte) 0x00, (byte) 0x00, (byte) 0xb0, (byte) 0x00, (byte) 0x00, (byte) 0xff},/* 41: Magnet voltage*/
            {(byte) 3, (byte) 0x00, (byte) 0x00, (byte) 0xb0, (byte) 0x00, (byte) 0x01, (byte) 0xff},/* 42: Temperature */
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0x07, (byte) 0x00, (byte) 0xff},/* 43: Active power+*/
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0x08, (byte) 0x00, (byte) 0xff},/* 44: Active power+ Wh*/
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0x1b, (byte) 0x00, (byte) 0xff},/* 45: Demand+ W Ave 15min*/
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x02, (byte) 0x07, (byte) 0x00, (byte) 0xff},/* 46: Active power-*/
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x02, (byte) 0x08, (byte) 0x00, (byte) 0xff},/* 47: Active power- Wh*/
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x02, (byte) 0x1b, (byte) 0x00, (byte) 0xff},/* 48: Demand- W Ave 15min*/
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x03, (byte) 0x08, (byte) 0x00, (byte) 0xff},/* 49: Reactive energy (delay)*/
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x04, (byte) 0x08, (byte) 0x00, (byte) 0xff},/* 50: Reactive energy (lead)*/
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x0b, (byte) 0x07, (byte) 0x00, (byte) 0xff},/* 51: Current0 */
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x0c, (byte) 0x03, (byte) 0x00, (byte) 0xff},/* 52: Minimum Voltage0*/
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x0c, (byte) 0x04, (byte) 0x00, (byte) 0xff},/* 53: Voltage0 Ave 1min value */
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x0c, (byte) 0x07, (byte) 0x00, (byte) 0xff},/* 54: Voltage0*/
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x0c, (byte) 0x1b, (byte) 0x00, (byte) 0xff},/* 55: Voltage0 Ave 15min value */
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x0d, (byte) 0x07, (byte) 0x00, (byte) 0xff},/* 56: Power factor0 */
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x0e, (byte) 0x07, (byte) 0x00, (byte) 0xff},/* 57: Supply frequency L1 */
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x0f, (byte) 0x08, (byte) 0x00, (byte) 0xff},/* 58: ABS Wh*/
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x10, (byte) 0x08, (byte) 0x00, (byte) 0xff},/* 59: NET Wh*/
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x1f, (byte) 0x07, (byte) 0x00, (byte) 0xff},/* 60: Current L1*/
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x1f, (byte) 0x1c, (byte) 0x01, (byte) 0xff},/* 61: Current L1 Ave 30min*/
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x20, (byte) 0x03, (byte) 0x00, (byte) 0xff},/* 62: Minimum Voltage of L1 */
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x20, (byte) 0x04, (byte) 0x00, (byte) 0xff},/* 63: Voltage L1 Ave 1min value */
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x20, (byte) 0x07, (byte) 0x00, (byte) 0xff},/* 64: Voltage L1*/
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x20, (byte) 0x1b, (byte) 0x00, (byte) 0xff},/* 65: Voltage L1 Ave 15min value*/
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x33, (byte) 0x07, (byte) 0x00, (byte) 0xff},/* 66: Current L2*/
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x34, (byte) 0x03, (byte) 0x00, (byte) 0xff},/* 67: Minimum Voltage of L2 */
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x34, (byte) 0x04, (byte) 0x00, (byte) 0xff},/* 68: Voltage L2 Ave 1min value */
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x34, (byte) 0x07, (byte) 0x00, (byte) 0xff},/* 69: Voltage L2*/
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x47, (byte) 0x05, (byte) 0x00, (byte) 0xff},/* 70: Current L3*/
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x48, (byte) 0x03, (byte) 0x00, (byte) 0xff},/* 71: Minimum Voltage of L3 */
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0x48, (byte) 0x07, (byte) 0x00, (byte) 0xff},/* 72: Voltage L3*/
            {(byte) 3, (byte) 0x01, (byte) 0x00, (byte) 0xa0, (byte) 0x00, (byte) 0x01, (byte) 0xff},/* 73: ActivePower+-*/
            {(byte) 4, (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0x06, (byte) 0x00, (byte) 0xff},/* 74: Max.Demand+ W(Last reset) */
            {(byte) 4, (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0x06, (byte) 0x01, (byte) 0xff},/* 75: Max.Demand+ W*/
            {(byte) 4, (byte) 0x01, (byte) 0x00, (byte) 0x02, (byte) 0x06, (byte) 0x00, (byte) 0xff},/* 76: Max.Demand- W(Last reset) */
            {(byte) 4, (byte) 0x01, (byte) 0x00, (byte) 0x02, (byte) 0x06, (byte) 0x01, (byte) 0xff},/* 77: Max.Demand- W*/
            {(byte) 7, (byte) 0x00, (byte) 0x00, (byte) 0x63, (byte) 0x62, (byte) 0x00, (byte) 0xff},/* 78: Power qualty log*/
            {(byte) 7, (byte) 0x00, (byte) 0x00, (byte) 0x63, (byte) 0x62, (byte) 0xff, (byte) 0xff},/* 79: Meter log*/
            {(byte) 7, (byte) 0x01, (byte) 0x00, (byte) 0x62, (byte) 0x01, (byte) 0x00, (byte) 0xff},/* 80: Billing  record*/
            {(byte) 7, (byte) 0x01, (byte) 0x00, (byte) 0x63, (byte) 0x01, (byte) 0x00, (byte) 0xff},/* 81: Load profile record*/
            {(byte) 7, (byte) 0x01, (byte) 0x00, (byte) 0xb0, (byte) 0x00, (byte) 0x01, (byte) 0xff},/* 82: Reactive energy 30min data*/
            {(byte) 7, (byte) 0x01, (byte) 0x00, (byte) 0xb0, (byte) 0x00, (byte) 0x02, (byte) 0xff},/* 83: Ampare record*/
            {(byte) 7, (byte) 0x01, (byte) 0x00, (byte) 0xb0, (byte) 0x00, (byte) 0x03, (byte) 0xff},/* 84: Specification*/
            {(byte) 7, (byte) 0x01, (byte) 0x00, (byte) 0xb0, (byte) 0x00, (byte) 0x04, (byte) 0xff},/* 85: Current measure*/
            {(byte) 8, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0xff},/* 86: Datetime*/
            {(byte) 8, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x01, (byte) 0xff},/* 87: Datetime(RTC)*/
            {(byte) 9, (byte) 0x00, (byte) 0x00, (byte) 0x0a, (byte) 0x00, (byte) 0x01, (byte) 0xff},/* 88: Demand reset*/
            {(byte) 15, (byte) 0x00, (byte) 0x00, (byte) 0x28, (byte) 0x00, (byte) 0x00, (byte) 0xff},/* 89: CurrentAsso */
            {(byte) 15, (byte) 0x00, (byte) 0x00, (byte) 0x28, (byte) 0x00, (byte) 0x01, (byte) 0xff},/* 90: Association LN(PUB)*/
            {(byte) 15, (byte) 0x00, (byte) 0x00, (byte) 0x28, (byte) 0x00, (byte) 0x02, (byte) 0xff},/* 91: Association LN(HLS)*/
            {(byte) 15, (byte) 0x00, (byte) 0x00, (byte) 0x28, (byte) 0x00, (byte) 0x03, (byte) 0xff},/* 92: Association LN(LLS)*/
            {(byte) 18, (byte) 0x00, (byte) 0x00, (byte) 0x2c, (byte) 0x00, (byte) 0x00, (byte) 0xff},/* 93: Image taransfer */
            {(byte) 19, (byte) 0x00, (byte) 0x00, (byte) 0x14, (byte) 0x00, (byte) 0x00, (byte) 0xff},/* 94: Optical port setup object */
            {(byte) 23, (byte) 0x00, (byte) 0x00, (byte) 0x16, (byte) 0x00, (byte) 0x00, (byte) 0xff},/* 95: HDLC Setting(Optical)*/
            {(byte) 23, (byte) 0x00, (byte) 0x01, (byte) 0x16, (byte) 0x00, (byte) 0x00, (byte) 0xff},/* 96: HDLC Setting(Other)*/
            {(byte) 64, (byte) 0x00, (byte) 0x00, (byte) 0x2b, (byte) 0x00, (byte) 0x00, (byte) 0xff},/* 97: Setup security(For security NON)*/
            {(byte) 64, (byte) 0x00, (byte) 0x00, (byte) 0x2b, (byte) 0x00, (byte) 0x01, (byte) 0xff},/* 98: Setup security(For security HLS)*/
            {(byte) 64, (byte) 0x00, (byte) 0x00, (byte) 0x2b, (byte) 0x00, (byte) 0x02, (byte) 0xff},/* 99: Setup security(For security LLS) */
    };
    private final static byte AARQ = (byte) 0x60;
    private final static byte AARE = (byte) 0x61;
    private final static byte RLRQ = (byte) 0x62;
    private final static byte initQ = (byte) 0x01;
    private final static byte glo_iniQ = (byte) 33;
    private final static byte glo_getQ = (byte) 200;
    private final static byte glo_setQ = (byte) 201;
    private final static byte glo_actQ = (byte) 203;
    private final static byte ded_getQ = (byte) 208;
    private final static byte ded_setQ = (byte) 209;
    private final static byte ded_actQ = (byte) 211;
    private final static byte[] def_app1 = {(byte) 0xa1, (byte) 0x09, (byte) 0x06, (byte) 0x07, (byte) 0x60, (byte) 0x85, (byte) 0x74, (byte) 0x05, (byte) 0x08, (byte) 0x01, (byte) 0x01};
    private final static byte[] def_app6 = {(byte) 0xa6, (byte) 0x0a, (byte) 0x04, (byte) 0x08, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44, (byte) 0x55};
    private final static byte[] def_app10 = {(byte) 0x8a, (byte) 0x02, (byte) 0x07, (byte) 0x80};
    private final static byte[] def_app11 = {(byte) 0x8b, (byte) 0x07, (byte) 0x60, (byte) 0x85, (byte) 0x74, (byte) 0x05, (byte) 0x08, (byte) 0x02, (byte) 0x01};
    private final static byte def_app12 = (byte) 0xac;//(byte) 0x12, (byte) 0x80, (byte) 0x10};
    private final static byte def_app30 = (byte) 0xbe;//(byte) 0x10, (byte) 0x04, (byte) 0x0e};
    private final static byte[] def_conf = {(byte) 0x00, (byte) 0x00, (byte) 0x06, (byte) 0x5f, (byte) 0x1f, (byte) 0x04, (byte) 0x00, (byte) 0x00, (byte) 0x10, (byte) 0x1d, (byte) 0x00, (byte) 0xD0};/*224*/
    //    private final static byte [] def_conf = {(byte) 0x00, (byte) 0x00, (byte) 0x06, (byte) 0x5f, (byte) 0x1f, (byte) 0x04, (byte) 0x00, (byte) 0x00, (byte) 0x10, (byte) 0x1d, (byte) 0x03, (byte) 0x00};
    private final static byte[] def_rlrq = {(byte) 0x80, (byte) 0x01, (byte) 0x00};/*224*/
    private final static byte[] GETRQ = {
            (byte) 0xc0, (byte) 0x01, (byte) 0x41,
            (byte) 0x00, (byte) 0x01,
            (byte) 0x01, (byte) 0x41, (byte) 0x00, (byte) 0x00, (byte) 0x02, (byte) 0xff,
            (byte) 0x02, (byte) 0x00
    };
    private final static byte[] GTNRQ = {
            (byte) 0xc0, (byte) 0x02, (byte) 0x41, 0x00, 0x00, 0x00, 0x00
    };
    private final static byte[] SETRQ = {
            (byte) 0xc1, (byte) 0x01, (byte) 0x41,
            (byte) 0x00, (byte) 0x03,
            (byte) 0x01, (byte) 0x41, (byte) 0x00, (byte) 0x20, (byte) 0x00, (byte) 0xff,
            (byte) 0x02, (byte) 0x00
    };//
    private final static byte[] ACTRQ = {
            (byte) 0xc3, (byte) 0x01, (byte) 0x41,
            (byte) 0x00, (byte) 0x03,
            (byte) 0x00, (byte) 0x00, (byte) 0x60, (byte) 0x0f, (byte) 0x00, (byte) 0xff,
            (byte) 0x01
    };
    private final static String[] ERROR = {
            "Success",// = (0),
            "Hardware fault",// = (1),
            "Temporary failure",// = (2),
            "Read write denied",// = (3),
            "Object undefined",// = (4),
            "Object class inconsistent",// = (9),
            "Object unavailable",// = (11),
            "Type unmatched",// = (12),
            "Scope of access violated",// = (13),
            "Data block unavailable",// = (14),
            "Long get aborted",// = (15),
            "No long get in progress",// = (16),
            "Long set aborted",// = (17),
            "No long set in progress",// = (18),
            "Data block number invalid",// = (19),
            "Other reason",// = (250)
    };
    private final static String[] alert1 = {
            "Clock invalid(0)",
            "unregistered(1)",
            "unregistered(2)",
            "unregistered(3)",
            "unregistered(4)",
            "unregistered(5)",
            "unregistered(6)",
            "unregistered(7)",
            "Program memory error(8)",
            "unregistered(9)",
            "unregistered(10)",
            "unregistered(11)",
            "unregistered(12)",
            "Missing neutral(13)",
            "Phase and neutral interchange(14)",
            "In and out interchange(15)",
            "Terminal cover open(16)",
            "unregistered(17)",
            "unregistered(18)",
            "unregistered(19)",
            "unregistered(20)",
            "unregistered(21)",
            "unregistered(22)",
            "unregistered(23)",
            "unregistered(24)",
            "unregistered(25)",
            "unregistered(26)",
            "unregistered(27)",
            "unregistered(28)",
            "unregistered(29)",
            "unregistered(30)",
            "unregistered(31)"
    };
    private final static String[] alert2 = {
            "Total Power Failure(0)",
            "Power Resume(1)",
            "Missing L1 volt (2)",
            "Missing L2 volt (3)",
            "Missing L3 volt (4)",
            "Normal L1 volt(5)",
            "Normal L2 volt(6)",
            "Normal L3 volt(7)",
            "unregistered(8)",
            "unregistered(9)",
            "Current Reversal(10)",
            "Wrong Phase Sequence(11)",
            "unregistered(12)",
            "unregistered(13)",
            "Bad Voltage Quality L1(14)",
            "Bad Voltage Quality L2(15)",
            "Bad Voltage Quality L3(16)",
            "unregistered(17)",
            "Local communication attempt(18)",
            "unregistered(19)",
            "unregistered(20)",
            "unregistered(21)",
            "unregistered(22)",
            "unregistered(23)",
            "unregistered(24)",
            "unregistered(25)",
            "unregistered(26)",
            "unregistered(27)",
            "unregistered(28)",
            "unregistered(29)",
            "unregistered(30)",
            "unregistered(31)",
    };
    public static String[] PQCODE =
            {
                    "UNDEF(0)",
                    "RSV(1)",
                    "RSV(2)",
                    "RSV(3)",
                    "RSV(4)",
                    "RSV(5)",
                    "RSV(6)",
                    "RSV(7)",
                    "RSV(8)",
                    "RSV(9)",
                    "RSV(10)",
                    "RSV(11)",
                    "RSV(12)",
                    "RSV(13)",
                    "RSV(14)",
                    "RSV(15)",
                    "RSV(16)",
                    "RSV(17)",
                    "RSV(18)",
                    "RSV(19)",
                    "RSV(20)",
                    "RSV(21)",
                    "RSV(22)",
                    "RSV(23)",
                    "RSV(24)",
                    "RSV(25)",
                    "RSV(26)",
                    "RSV(27)",
                    "RSV(28)",
                    "RSV(29)",
                    "RSV(30)",
                    "RSV(31)",
                    "RSV(32)",
                    "RSV(33)",
                    "RSV(34)",
                    "RSV(35)",
                    "RSV(36)",
                    "RSV(37)",
                    "RSV(38)",
                    "RSV(39)",
                    "RSV(40)",
                    "RSV(41)",
                    "RSV(42)",
                    "RSV(43)",
                    "RSV(44)",
                    "RSV(45)",
                    "RSV(46)",
                    "RSV(47)",
                    "RSV(48)",
                    "RSV(49)",
                    "RSV(50)",
                    "RSV(51)",
                    "RSV(52)",
                    "RSV(53)",
                    "RSV(54)",
                    "RSV(55)",
                    "RSV(56)",
                    "RSV(57)",
                    "RSV(58)",
                    "RSV(59)",
                    "RSV(60)",
                    "RSV(61)",
                    "RSV(62)",
                    "RSV(63)",
                    "RSV(64)",
                    "RSV(65)",
                    "RSV(66)",
                    "RSV(67)",
                    "RSV(68)",
                    "RSV(69)",
                    "RSV(70)",
                    "RSV(71)",
                    "RSV(72)",
                    "RSV(73)",
                    "RSV(74)",
                    "RSV(75)",
                    "RSV(76)",
                    "RSV(77)",
                    "RSV(78)",
                    "RSV(79)",
                    "RSV(80)",
                    "RSV(81)",
                    "RSV(82)",
                    "RSV(83)",
                    "RSV(84)",
                    "Normal voltage1",
                    "Normal voltage2",
                    "Normal voltage3",
                    "RSV(88)",
                    "RSV(89)",
                    "RSV(90)",
                    "RSV(91)",
                    "RSV(92)",
                    "RSV(93)",
                    "RSV(94)",
                    "RSV(95)",
                    "RSV(96)",
                    "RSV(97)",
                    "RSV(98)",
                    "RSV(99)",
                    "RSV(100)",
                    "RSV(101)",
                    "RSV(102)",
                    "RSV(103)",
                    "RSV(104)",
                    "RSV(105)",
                    "RSV(106)",
                    "RSV(107)",
                    "RSV(108)",
                    "RSV(109)",
                    "RSV(110)",
                    "RSV(111)",
                    "RSV(112)",
                    "RSV(113)",
                    "RSV(114)",
                    "RSV(115)",
                    "RSV(116)",
                    "RSV(117)",
                    "RSV(118)",
                    "RSV(119)",
                    "RSV(120)",
                    "RSV(121)",
                    "RSV(122)",
                    "RSV(123)",
                    "RSV(124)",
                    "RSV(125)",
                    "RSV(126)",
                    "RSV(127)",
                    "RSV(128)",
                    "RSV(129)",
                    "RSV(130)",
                    "RSV(131)",
                    "RSV(132)",
                    "RSV(133)",
                    "RSV(134)",
                    "RSV(135)",
                    "RSV(136)",
                    "RSV(137)",
                    "RSV(138)",
                    "RSV(139)",
                    "RSV(140)",
                    "RSV(141)",
                    "RSV(142)",
                    "RSV(143)",
                    "RSV(144)",
                    "RSV(145)",
                    "RSV(146)",
                    "RSV(147)",
                    "RSV(148)",
                    "RSV(149)",
                    "RSV(150)",
                    "RSV(151)",
                    "RSV(152)",
                    "RSV(153)",
                    "RSV(154)",
                    "RSV(155)",
                    "RSV(156)",
                    "RSV(157)",
                    "RSV(158)",
                    "RSV(159)",
                    "RSV(160)",
                    "RSV(161)",
                    "RSV(162)",
                    "RSV(163)",
                    "RSV(164)",
                    "RSV(165)",
                    "RSV(166)",
                    "RSV(167)",
                    "RSV(168)",
                    "RSV(169)",
                    "RSV(170)",
                    "RSV(171)",
                    "RSV(172)",
                    "RSV(173)",
                    "RSV(174)",
                    "RSV(175)",
                    "RSV(176)",
                    "RSV(177)",
                    "RSV(178)",
                    "RSV(179)",
                    "RSV(180)",
                    "RSV(181)",
                    "RSV(182)",
                    "RSV(183)",
                    "RSV(184)",
                    "RSV(185)",
                    "RSV(186)",
                    "RSV(187)",
                    "RSV(188)",
                    "RSV(189)",
                    "RSV(190)",
                    "RSV(191)",
                    "RSV(192)",
                    "RSV(193)",
                    "RSV(194)",
                    "RSV(195)",
                    "RSV(196)",
                    "RSV(197)",
                    "RSV(198)",
                    "RSV(199)",
                    "RSV(200)",
                    "RSV(201)",
                    "Bad voltage1",
                    "Bad voltage1",
                    "Bad voltage1",
                    "UNDEF(205)",
                    "UNDEF(206)",
                    "UNDEF(207)",
                    "UNDEF(208)",
                    "Over voltage1",
                    "Over voltage2",
                    "Over voltage3",
                    "UNDEF(212)",
                    "UNDEF(213)",
                    "UNDEF(214)",
                    "UNDEF(215)",
                    "UNDEF(216)",
                    "UNDEF(217)",
                    "UNDEF(218)",
                    "UNDEF(219)",
                    "Invalid clock",
                    "Changed clock",
                    "Invalid data",
                    "Power failure",
                    "Power resume",
                    "Program error",
                    "Program recover",
                    "Battery low",
                    "Battery recover",
                    "Missing volt",
                    "Detect volt",
                    "UNBALANCE(231)",
                    "BALANCED(232)",
                    "Current reversal",
                    "Current observe",
                    "P-N interchange",
                    "N-P interchange",
                    "Missing neutral",
                    "Detect neutral",
                    "Local communication attempt(239)",
                    "Local communication exit(240)",
                    "Battery level changed",
                    "UNDEF(242)",
                    "UNDEF(243)",
                    "UNDEF(244)",
                    "UNDEF(245)",
                    "UNDEF(246)",
                    "UNDEF(247)",
                    "UNDEF(248)",
                    "UNDEF(249)",
                    "UNDEF(250)",
                    "UNDEF(251)",
                    "UNDEF(252)",
                    "UNDEF(253)",
                    "Load profile cleared",
                    "Event records cleared"
            };
    public static String[] LOGCODE =
            {
                    "Unknown",//0:予約
                    "LOG_FUKUDEN",//1:復電検出
                    "LOG_TEIDEN",//2:停電検出
                    "LOG_CHGLVI",//3:WDT検出
                    "LOG_CLK_INV",//4:予約
                    "LOG_CLK_INVR",//5:予約
                    "LOG_KIDO",//6:
                    "LOG_CPU",//7:LVIによるﾘｾｯﾄ
                    "LOG_RTC",//8:RTC状態判別
                    "LOG_ONM",//9:
                    "LOG_FLASH",//10:FWUpdate発生
                    "LOG_SIGN",//11:シグニチャ更新
                    "LOG_CLKSET",//12:時刻変更
                    "LOG_KEYCHG",//13:鍵交換
                    "LOG_RECRST",//14:レコードリセット
                    "LOG_ALM",//15:予約
                    "LOG_BATTLV",//16:予約
                    "LOG_OBJMAP",//17:予約
                    "LOG_MANUAL",//18:手動ログ
                    "LOG_METER",//19:予約
                    "LOG_DEFSET",//20:予約
                    "LOG_USERSET",//21:予約
                    "LOG_RESET",//22:予約
                    "LOG_MEMORY",//23:予約
                    "LOG_CMD",//24:コマンド通信
                    "LOG_BATT",//25:予約
                    "LOG_BATTR",//26:予約
                    "LOG_TSN_OPT",//27:Optical通信開始
                    "LOG_TSN_OPTR",//28:Optical通信終了
                    "LOG_MISS_V",//1側電圧喪失検出(検出解除相関ｱﾘ)
                    "LOG_MISS_VR",//1側電圧喪失解除
                    "LOG_VLT_HI",//31:1側電圧上昇検出(検出解除相関ｱﾘ)
                    "LOG_VLT_HIR",//1側電圧上昇解除
                    "LOG_VLT_LO",//1側電圧低下検出(検出解除相関ｱﾘ)
                    "LOG_VLT_LOR",//1側電圧低下解除
                    "LOG_MAG",//磁気検出(検出解除相関ｱﾘ)
                    "LOG_MAGR",//磁気解除
                    "LOG_AMP_A",//電流タンパーA(検出解除相関ｱﾘ)
                    "LOG_AMP_AR",//電流タンパーA解除
                    "LOG_AMP_B",//電流タンパーB(検出解除相関ｱﾘ)
                    "LOG_AMP_BR",//電流タンパーB解除
                    "LOG_AMP_N",//電流タンパーC(検出解除相関ｱﾘ)
                    "LOG_AMP_NR",//電流タンパーC解除
                    "LOG_AMP_D",//電流タンパーD(検出解除相関ｱﾘ)
                    "LOG_AMP_DR",//電流タンパーD解除
                    "LOG_CLIENT",//電流タンパーD解除
                    "LOG_ACCTBL",//電流タンパーD解除
                    "LOG_AMP_L",//電流タンパーD(検出解除相関ｱﾘ)
                    "LOG_AMP_LR",//電流タンパーD解除
                    "ELOG_CPU",//
                    "ELOG_CPUR",//
                    "ELOG_WAT",//有効電力異常による故障発生
                    "ELOG_WATR",//有効電力異常による故障復帰
                    "ELOG_VAR",//無効電力異常による故障発生
                    "ELOG_VARR",//無効電力異常による故障復帰
                    "ELOG_VI",//電圧･電流固定異常による故障発生
                    "ELOG_VIR",//電圧･電流固定異常による故障復帰
                    "ELOG_EEP",//EEPROMﾉ読ﾐ書ｷ異常による故障発生
                    "ELOG_EEPR",//EEPROMﾉ読ﾐ書ｷ異常による故障発生
                    "ELOG_RTC",//
                    "ELOG_RTCR",//
                    "ELOG_MAG",//
                    "ELOG_MAGR",//
                    "ELOG_TSN",//
                    "ELOG_TSNR",//
                    "ETSN1",//
                    "ETSN2",//
            };
    static String[] MonthList = {
            "----",
            "January",
            "February",
            "March",
            "April",
            "May",
            "June",
            "July",
            "August",
            "September",
            "October",
            "November",
            "December"
    };
    private static File folderFiles;
    private static int mScanTick;
    private static int mTick;
    private final String TAG = DLMS.class.getSimpleName();
    private final int[] YEAR = {
            0,      //	365	2010	2	365
            365,    //	365	2010	2	365
            730,    //	730	2011	3	365
            1096,   //	1096	2012	0	366
            1461,   //	1461	2013	1	365
            1826,   //	1826	2014	2	365
            2191,   //	2191	2015	3	365
            2557,   //	2557	2016	0	366
            2922,   //	2922	2017	1	365
            3287,   //	3287	2018	2	365
            3652,   //	3652	2019	3	365
            4018,   //	4018	2020	0	366
            4383,   //	4383	2021	1	365
            4748,   //	4748	2022	2	365
            5113,   //	5113	2023	3	365
            5479,   //	5479	2024	0	366
            5844,   //	5844	2025	1	365
            6209,   //	6209	2026	2	365
            6574,   //	6574	2027	3	365
            6940,   //	6940	2028	0	366
            7305,   //	7305	2029	1	365
            7670,   //	7670	2030	2	365
            8035,   //	8035	2031	3	365
            8401,   //	8401	2032	0	366
            8766,   //	8766	2033	1	365
            9131,   //	9131	2034	2	365
            9496,   //	9496	2035	3	365
            9862,   //	9862	2036	0	366
            10227,  //	10227	2037	1	365
            10592,  //	10592	2038	2	365
            10957,  //	10957	2039	3	365
            11323,  //	11323	2040	0	366
            11688,  //	11688	2041	1	365
            12053,  //	12053	2042	2	365
            12418,  //	12418	2043	3	365
            12784,  //	12784	2044	0	366
            13149,  //	13149	2045	1	365
            13514,//	13514	2046	2	365
            13879,//	13879	2047	3	365
            14245,//	14245	2048	0	366
            14610,//	14610	2049	1	365
            14975,//	14975	2050	2	365
            15340,//	15340	2051	3	365
            15706,//	15706	2052	0	366
            16071,//	16071	2053	1	365
            16436,//	16436	2054	2	365
            16801,//	16801	2055	3	365
            17167,//	17167	2056	0	366
            17532,//	17532	2057	1	365
            17897,//	17897	2058	2	365
            18262,//	18262	2059	3	365
            18628,//	18628	2060	0	366
            18993,//	18993	2061	1	365
            19358,//	19358	2062	2	365
            19723,//	19723	2063	3	365
            20089,//	20089	2064	0	366
            20454,//	20454	2065	1	365
            20819,//	20819	2066	2	365
            21184,//	21184	2067	3	365
            21550,//	21550	2068	0	366
            21915,//	21915	2069	1	365
            22280,//	22280	2070	2	365
            22645,//	22645	2071	3	365
            23011,//	23011	2072	0	366
            23376,//	23376	2073	1	365
            23741,//	23741	2074	2	365
            24106,//	24106	2075	3	365
            24472,//	24472	2076	0	366
            24837,//	24837	2077	1	365
            25202,//	25202	2078	2	365
            25567,//	25567	2079	3	365
            25933,//	25933	2080	0	366
            26298,//	26298	2081	1	365
            26663,//	26663	2082	2	365
            27028,//	27028	2083	3	365
            27394,//	27394	2084	0	366
            27759,//	27759	2085	1	365
            28124,//	28124	2086	2	365
            28489,//	28489	2087	3	365
            28855,//	28855	2088	0	366
            29220,//	29220	2089	1	365
            29585,//	29585	2090	2	365
            29950,//	29950	2091	3	365
            30316,//	30316	2092	0	366
            30681,//	30681	2093	1	365
            31046,//	31046	2094	2	365
            31411,//	31411	2095	3	365
            31777,//	31777	2096	0	366
            32142,//	32142	2097	1	365
            32507,//	32507	2098	2	365
            32872,//	32872	2099	3	365
            33238//	33238	2100	0	366
    };
    private final int[] MONTH = {
            0,
            31,        //31
            59,        //28
            90,        //31
            120,    //30
            151,    //31
            181,    //30
            212,    //31
            243,    //31
            273,    //30
            304,    //31
            334,    //30
            365,    //31
    };
    private final int[] fcs16Table = {
            0x0000, 0x1189, 0x2312, 0x329B, 0x4624, 0x57AD, 0x6536, 0x74BF,
            0x8C48, 0x9DC1, 0xAF5A, 0xBED3, 0xCA6C, 0xDBE5, 0xE97E, 0xF8F7,
            0x1081, 0x0108, 0x3393, 0x221A, 0x56A5, 0x472C, 0x75B7, 0x643E,
            0x9CC9, 0x8D40, 0xBFDB, 0xAE52, 0xDAED, 0xCB64, 0xF9FF, 0xE876,
            0x2102, 0x308B, 0x0210, 0x1399, 0x6726, 0x76AF, 0x4434, 0x55BD,
            0xAD4A, 0xBCC3, 0x8E58, 0x9FD1, 0xEB6E, 0xFAE7, 0xC87C, 0xD9F5,
            0x3183, 0x200A, 0x1291, 0x0318, 0x77A7, 0x662E, 0x54B5, 0x453C,
            0xBDCB, 0xAC42, 0x9ED9, 0x8F50, 0xFBEF, 0xEA66, 0xD8FD, 0xC974,
            0x4204, 0x538D, 0x6116, 0x709F, 0x0420, 0x15A9, 0x2732, 0x36BB,
            0xCE4C, 0xDFC5, 0xED5E, 0xFCD7, 0x8868, 0x99E1, 0xAB7A, 0xBAF3,
            0x5285, 0x430C, 0x7197, 0x601E, 0x14A1, 0x0528, 0x37B3, 0x263A,
            0xDECD, 0xCF44, 0xFDDF, 0xEC56, 0x98E9, 0x8960, 0xBBFB, 0xAA72,
            0x6306, 0x728F, 0x4014, 0x519D, 0x2522, 0x34AB, 0x0630, 0x17B9,
            0xEF4E, 0xFEC7, 0xCC5C, 0xDDD5, 0xA96A, 0xB8E3, 0x8A78, 0x9BF1,
            0x7387, 0x620E, 0x5095, 0x411C, 0x35A3, 0x242A, 0x16B1, 0x0738,
            0xFFCF, 0xEE46, 0xDCDD, 0xCD54, 0xB9EB, 0xA862, 0x9AF9, 0x8B70,
            0x8408, 0x9581, 0xA71A, 0xB693, 0xC22C, 0xD3A5, 0xE13E, 0xF0B7,
            0x0840, 0x19C9, 0x2B52, 0x3ADB, 0x4E64, 0x5FED, 0x6D76, 0x7CFF,
            0x9489, 0x8500, 0xB79B, 0xA612, 0xD2AD, 0xC324, 0xF1BF, 0xE036,
            0x18C1, 0x0948, 0x3BD3, 0x2A5A, 0x5EE5, 0x4F6C, 0x7DF7, 0x6C7E,
            0xA50A, 0xB483, 0x8618, 0x9791, 0xE32E, 0xF2A7, 0xC03C, 0xD1B5,
            0x2942, 0x38CB, 0x0A50, 0x1BD9, 0x6F66, 0x7EEF, 0x4C74, 0x5DFD,
            0xB58B, 0xA402, 0x9699, 0x8710, 0xF3AF, 0xE226, 0xD0BD, 0xC134,
            0x39C3, 0x284A, 0x1AD1, 0x0B58, 0x7FE7, 0x6E6E, 0x5CF5, 0x4D7C,
            0xC60C, 0xD785, 0xE51E, 0xF497, 0x8028, 0x91A1, 0xA33A, 0xB2B3,
            0x4A44, 0x5BCD, 0x6956, 0x78DF, 0x0C60, 0x1DE9, 0x2F72, 0x3EFB,
            0xD68D, 0xC704, 0xF59F, 0xE416, 0x90A9, 0x8120, 0xB3BB, 0xA232,
            0x5AC5, 0x4B4C, 0x79D7, 0x685E, 0x1CE1, 0x0D68, 0x3FF3, 0x2E7A,
            0xE70E, 0xF687, 0xC41C, 0xD595, 0xA12A, 0xB0A3, 0x8238, 0x93B1,
            0x6B46, 0x7ACF, 0x4854, 0x59DD, 0x2D62, 0x3CEB, 0x0E70, 0x1FF9,
            0xF78F, 0xE606, 0xD49D, 0xC514, 0xB1AB, 0xA022, 0x92B9, 0x8330,
            0x7BC7, 0x6A4E, 0x58D5, 0x495C, 0x3DE3, 0x2C6A, 0x1EF1, 0x0F78
    };
    private final byte[] def_info = {
            (byte) 0x81, (byte) 0x80, (byte) 0x0c,
            (byte) 0x05, (byte) 0x01, (byte) 0x00,
            (byte) 0x06, (byte) 0x01, (byte) 0x00,
            (byte) 0x07, (byte) 0x01, (byte) 0x00,
            (byte) 0x08, (byte) 0x01, (byte) 0x00
    };
    private int seed0;
    private long seed1, seed2, seed3;
    private int mCurrentAcount = 0;
    private final ArrayList<AccountInfo> AccountInformation;
    private final Context mContext;
    private Calendar old;
    // HLS implementation
    private AccSecurity accSecurity;
    private boolean hlsActive = false;
    private final String hlsEncryptionKeyAlias = "myMeterEncryptionKey"; // Example alias
    private final byte[] clientSystemTitle = {(byte) 0x41, (byte) 0x42, (byte) 0x43, (byte) 0x30, (byte) 0x30, (byte) 0x30, (byte) 0x30, (byte) 0x31}; // Example
    private byte[] serverSystemTitle; // This should be obtained from the meter, e.g., during AARE
    private byte[] app1;
    private byte[] app6;
    private byte[] app10;
    private byte[] app11;
    private byte[] app12;
    private byte[] app30;
    private int client;
    private final byte[] challenge0 = new byte[31];
    private final byte[] frameCounter0 = new byte[4];
    private byte[] challenge1;
    private byte[] master;
    private byte[] dedicate;
    private final byte[] adr1 = {0x03};
    private byte cmd;
    private byte ns, nr;
    private byte ws, wr, cws, cwr;
    private int ss, sr;
    private byte[] info;
    private int mRank;
    private byte[] svChallenge = null;
    private byte[] clChallenge = null;
    private byte[] svAppTitle = null;
    private byte[] clAppTitle = null;
    private int mObj;
    private byte mMode;
    private byte mAtr;
    private byte mSel;
    private int mBlockNo;
    private byte[] global;

    public DLMS(Context context) {
        mContext = context;
        AccountInformation = new ArrayList<AccountInfo>();
        AccountInfo level0 = new AccountInfo();
        level0.set("Super   ,00000000000000000000000000000000,7f,00");
        AccountInformation.add(level0);

        AccountInfo level1 = new AccountInfo();
        level1.set("Admin   ,30303030303030303030303030303030,61,01");
        AccountInformation.add(level1);

        AccountInfo level2 = new AccountInfo();
        level2.set("Power   ,3030303030303030,41,02");
        AccountInformation.add(level2);

        AccountInfo level3 = new AccountInfo();
        level3.set("Reader  ,3030303030303030,41,03");
        AccountInformation.add(level3);

        byte[] placeholderServerTitle = {(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00}; // Placeholder
        accSecurity = new AccSecurity(mContext, clientSystemTitle, placeholderServerTitle);

        // Example: Ensure a key exists in Keystore (you might do this once during setup)
        SecretKey masterKey = accSecurity.getSecretKeyFromKeystore(hlsEncryptionKeyAlias);
        if (masterKey == null) {
            masterKey = accSecurity.generateAndStoreAesGcmKey(hlsEncryptionKeyAlias);
            if (masterKey != null) {
                // Key generated and stored
            } else {
                // Handle key generation failure
            }
        }
        // This masterKey might be used directly or to derive session keys
        // For HLS, often a session key is derived/agreed upon.
        // For simplicity here, let's assume masterKey is used as session key (not always the case)
        accSecurity.setSessionEncryptionKey(masterKey);

    }

    public static String dateTimeToMonth(final String DateTime) {
        int pos = Integer.parseInt(DateTime.substring(5, 7));
        return String.format("%s %s", MonthList[pos], DateTime.substring(0, 4));
    }

    public static String getNowDate() {
        android.icu.text.SimpleDateFormat sdf = new android.icu.text.SimpleDateFormat("EEE dd MMM yyyy HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date());
    }

    // Call this after successful AARE with HLS confirmed and server system title is known
    public void activateHls(byte[] meterSystemTitle, SecretKey agreedSessionKey) {
        this.serverSystemTitle = meterSystemTitle;
        // Re-initialize or update AccSecurity if server system title changes or wasn't known
        this.accSecurity = new AccSecurity(mContext, clientSystemTitle, this.serverSystemTitle);
        this.accSecurity.setSessionEncryptionKey(agreedSessionKey); // Set the actual session key
        this.hlsActive = true;
        // accSecurity.resetInvocationCounters(); // Or similar method
    }

    public byte[] get_request_normal(int object, int clas, int attribute, int version, int function) {
        // ... (construct your normal APDU: PDU)
        byte[] pdu = constructPlaintextGetRequest(object, clas, attribute, version, function);

        if (hlsActive && accSecurity != null && accSecurity.getSessionEncryptionKey() != null) {
            // 1. Get current client invocation counter
            long frameCounter = accSecurity.getNextClientInvocationCounterAndIncrement();

            // 2. Encrypt the PDU
            byte[] securedPdu = accSecurity.encryptApdu(pdu, accSecurity.getSessionEncryptionKey(), clientSystemTitle, frameCounter);

            if (securedPdu != null) {
                // 3. Prepend the security header (containing security control byte, frame counter)
                //    to the securedPdu. The structure of this header is defined by DLMS.
                //    For example: [Security Control Byte] [Frame Counter (4 bytes)] [Secured APDU (Ciphertext + Tag)]
                byte[] securityHeader = createSecurityHeader(SECURITY_CONTROL_BYTE_ENCRYPTED_AUTHENTICATED, frameCounter);
                ByteBuffer finalApduBuffer = ByteBuffer.allocate(securityHeader.length + securedPdu.length);
                finalApduBuffer.put(securityHeader);
                finalApduBuffer.put(securedPdu);
                return finalApduBuffer.array();
            } else {
                // Handle encryption failure
                return null; // Or throw exception
            }
        } else {
            // Send unsecured (or handle error if HLS was expected)
            return pdu; // Or wrap in a non-secured APDU structure if needed
        }
    }

    public byte[] process_response(byte[] receivedData) {
        if (hlsActive && accSecurity != null && accSecurity.getSessionEncryptionKey() != null) {
            // 1. Parse the security header from receivedData
            //    - Extract security control byte
            //    - Extract server's frame counter
            //    - Get the actual encrypted payload (securedPduPart)
            // Example parsing (highly simplified):
            if (receivedData == null || receivedData.length < 5) { /* error: too short */
                return null;
            }
            byte securityControlByte = receivedData[0];
            ByteBuffer fcBuffer = ByteBuffer.wrap(receivedData, 1, 4);
            long serverFrameCounter = fcBuffer.getInt();
            byte[] securedPduPart = new byte[receivedData.length - 5];
            System.arraycopy(receivedData, 5, securedPduPart, 0, securedPduPart.length);

            // TODO: Validate serverFrameCounter against expected values to prevent replay attacks

            // 2. Decrypt the securedPduPart
            byte[] plaintextPdu = accSecurity.decryptApdu(securedPduPart, accSecurity.getSessionEncryptionKey(), serverSystemTitle, serverFrameCounter);

            if (plaintextPdu != null) {
                // Process the decrypted PDU
                return processPlaintextResponse(plaintextPdu);
            } else {
                // Handle decryption/authentication failure
                // This could mean data tampering or wrong key
                hlsActive = false; // Potentially invalidate HLS session
                accSecurity.resetSecurityContext();
                return null;
            }
        } else {
            // Process as unsecured response
            return processPlaintextResponse(receivedData);
        }
    }

    // Dummy helper methods (replace with actual DLMS APDU construction)
    private byte[] constructPlaintextGetRequest(int object, int clas, int attribute, int version, int function) {
        // Replace with your actual APDU construction logic
        String request = String.format("GET object:%d class:%d attr:%d ver:%d func:%d", object, clas, attribute, version, function);
        return request.getBytes(); // Example
    }

    private byte[] processPlaintextResponse(byte[] plaintextPdu) {
        // Replace with your actual response processing logic
        System.out.println("Plaintext response: " + new String(plaintextPdu));
        return plaintextPdu;
    }

    private byte[] createSecurityHeader(byte securityControl, long frameCounter) {
        ByteBuffer header = ByteBuffer.allocate(5); // 1 byte for control, 4 for counter
        header.put(securityControl);
        header.putInt((int) frameCounter); // Lower 4 bytes of frame counter
        return header.array();
    }

    public long DatetimeToSec(String datetime) {    /*yyyy/mm/dd hh:mm:ss*/
        int y = Integer.parseInt(datetime.substring(0, 4));
        int m = Integer.parseInt(datetime.substring(5, 7));
        int d = Integer.parseInt(datetime.substring(8, 10));
        int h = Integer.parseInt(datetime.substring(11, 13));
        int k = Integer.parseInt(datetime.substring(14, 16));
        int s = Integer.parseInt(datetime.substring(17, 19));

        int days = YEAR[y - 2010] + MONTH[m - 1] + d - 1;
        if ((y % 4) == 0) {
            if (m > 2) {
                days++;
            }
        }
        long sec = days;
        sec *= 86400;
        sec += h * 3600L;
        sec += k * 60L;
        sec += s;
        return sec;
    }

    public String SecToDatetime(final long sec) {

        int d;
        int m;
        int y;
        int h;
        int k;
        int s;

        d = (int) (sec / 86400);
        for (y = 0; y < 100; y++) {
            if (YEAR[y] > d) {
                y--;
                break;
            }
        }
        d -= YEAR[y];
        for (m = 0; m < 12; m++) {
            if (MONTH[m] > d) {
                m--;
                break;
            }
        }
        d -= MONTH[m];
        s = (int) (sec % 86400);
        h = s / 3600;
        s %= 3600;
        k = s / 60;
        s %= 60;
        return format("%04d/%02d/%02d %02d:%02d:%02d", y + 2010, m + 1, d, h, k, s);
    }

    public long CurrentDatetimeSec() {    /*yyyy/mm/dd hh:mm:ss*/

        long sec;
        String datetime;
        android.icu.text.SimpleDateFormat sdf = new android.icu.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault());
        Date date = new Date();
        sec = DatetimeToSec(sdf.format(date));
        return sec;
    }

    public String CurrentYearMonth() {    /*MMyyyy*/

        android.icu.text.SimpleDateFormat sdf = new android.icu.text.SimpleDateFormat("yyyyMM", Locale.getDefault());
        return sdf.format(new Date());
    }

    public String AnyYearMonth(final int year, final int month) {    /*MMyyyy*/
        Calendar start = Calendar.getInstance();
        start.setTime(new java.util.Date());
        start.add(Calendar.YEAR, year);
        start.add(Calendar.MONTH, month);
        return String.format("%4d%2d", start.get(Calendar.YEAR), start.get(Calendar.MONTH) + 1);
    }

    public String FormattedMonthDay(final int month, final int days) {    /*MMyyyy*/
        Calendar start = Calendar.getInstance();
        start.setTime(new java.util.Date());
        start.add(Calendar.MONTH, month);
        start.add(Calendar.DATE, days);
        int pos = start.get(Calendar.MONTH) + 1;
        return String.format("%s %2d, %4d", MonthList[pos], start.get(Calendar.DATE), start.get(Calendar.YEAR));
    }

    public String ConvertLocalDatetime(final String YearMonDay) {
        return String.format("%s/%s/%s",
                YearMonDay.substring(5, 7),  /*Mon*/
                YearMonDay.substring(8, 10),  /*Day*/
                YearMonDay.substring(0, 4)); /*Mon*/
    }

    public String SecToRawDatetime(final long sec) {

        int d;
        int m;
        int y;
        int h;
        int k;
        int s;

        d = (int) (sec / 86400);
        for (y = 0; y < 100; y++) {
            if (YEAR[y] > d) {
                y--;
                break;
            }
        }
        d -= YEAR[y];
        for (m = 0; m < 12; m++) {
            if (MONTH[m] > d) {
                m--;
                break;
            }
        }
        d -= MONTH[m];
        s = (int) (sec % 86400);
        h = s / 3600;
        s %= 3600;
        k = s / 60;
        s %= 60;
        return format("%04x%02x%02xff%02x%02x%02xff800000", y + 2010, m + 1, d, h, k, s);
    }

    public String TimeStampFilename(final String datetime) {
        String timestamp = datetime.replace("/", "");
        timestamp = timestamp.replace(":", "");
        timestamp = timestamp.replace(" ", "_");
        return timestamp;
    }

    private int getUI8(final byte[] in, final int offset) {
        int ret;
        if (in[offset] < 0)
            ret = 256 + in[offset];
        else
            ret = in[offset];
        return ret;
    }

    private int getI8(final byte[] in, final int offset) {
        int ret;
        ret = in[offset];
        return ret;
    }

    private int getUI16(final byte[] in, final int offset) {
        int ret;

        ret = 0;
        ret += getUI8(in, offset);
        ret <<= 8;
        ret += getUI8(in, offset + 1);
        return ret;
    }

    private int getI16(final byte[] in, final int offset) {
        int ret;
        ret = 0;
        ret += getUI8(in, offset);
        ret <<= 8;
        ret += getUI8(in, offset + 1);
        if (ret > 0x7fff) {
            ret -= 65536;
        }
        return ret;
    }

    private long getUI32(final byte[] in, final int offset) {
        long ret;
        ret = 0;
        ret += getUI8(in, offset);
        ret <<= 8;
        ret += getUI8(in, offset + 1);
        ret <<= 8;
        ret += getUI8(in, offset + 2);
        ret <<= 8;
        ret += getUI8(in, offset + 3);
        return ret;
    }

    private long getI32(final byte[] in, final int offset) {
        long ret;
        ret = 0;
        ret += getUI8(in, offset);
        ret <<= 8;
        ret += getUI8(in, offset + 1);
        ret <<= 8;
        ret += getUI8(in, offset + 2);
        ret <<= 8;
        ret += getUI8(in, offset + 3);
        if (ret > 0x7fffffff) {
            ret -= 0x100000000L;
        }
        return ret;
    }

    public String getBitsStr(final String bits) {
        StringBuffer ret = new StringBuffer();
        long eval = 1, val = Long.parseLong(bits);
        if (val > 0) {
            ret.append(format("%d (bit", val));
            for (int i = 0; i < 32; i++) {
                if ((val & eval) > 0) {
                    ret.append(format(" %d", i));
                }
                eval <<= 1;
            }
            ret.append(")");
        } else {
            ret.append(format("%X (off)", val));
        }
        return ret.toString();
    }

    public String setOct2Str(final byte[] oct, final int offset, final int length) {
        StringBuffer ret = new StringBuffer();
        for (int i = 0; i < length; i++) {
            ret.append(format("%02X", getUI8(oct, offset + i)));
        }
        return ret.toString();
    }

    public String setStr2Str(final byte[] oct, final int offset, final int length) {
        StringBuffer ret = new StringBuffer();
        for (int i = 0; i < length; i++) {
            ret.append(format("%c", getUI8(oct, offset + i)));
        }
        return ret.toString();
    }

    public byte[] setStr2Oct(final String str) {
        int len = str.length();
        byte[] out = new byte[len / 2 + len % 2];
        byte[] in = str.getBytes();

        int dat;
        for (int i = 0; i < in.length; i++) {
            dat = getUI8(in, i);
            switch (dat) {
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    dat -= '0';
                    break;
                case 'A':
                case 'B':
                case 'C':
                case 'D':
                case 'E':
                case 'F':
                    dat -= 'A';
                    dat += 0x0a;
                    break;
                case 'a':
                case 'b':
                case 'c':
                case 'd':
                case 'e':
                case 'f':
                    dat -= 'a';
                    dat += 0x0a;
                    break;
            }
            if ((i % 2) > 0) {
                out[i / 2] |= dat;
            } else {
                out[i / 2] = (byte) (dat << 4);
            }
        }
        return out;
    }

    public int setUInt32(byte[] buff, final int offset, final int val) {
        int dat = val;
        buff[offset + 3] = (byte) (dat & 0xff);
        dat >>= 8;
        buff[offset + 2] = (byte) (dat & 0xff);
        dat >>= 8;
        buff[offset + 1] = (byte) (dat & 0xff);
        dat >>= 8;
        buff[offset] = (byte) (dat & 0xff);
        return (offset + 4);
    }

    public int setUInt16(byte[] buff, final int offset, final int val) {
        int dat = val;
        buff[offset + 1] = (byte) (dat & 0xff);
        dat >>= 8;
        buff[offset] = (byte) (dat & 0xff);
        return (offset + 2);
    }

    public int setUInt8(byte[] buff, final int offset, final int val) {
        int dat = val;
        buff[offset] = (byte) (dat & 0xff);
        return (offset + 1);
    }

    public double Float(final double div, final String in) {
        return ((double) Long.parseLong(in)) / div;
    }

    public String arrange6_int(final String in) {
        Long val = Long.parseLong(in);
        return format("%06d", val);
    }

    public String arrange_boolean(final String t, final String f, final String in) {
        if (in.equals("false")) {
            return f;
        } else {
            return t;
        }
    }

    void writeFile(String data, File file) {
        // try-with-resources
        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(data + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ファイルを読み出し*---
    String readFile(File file) {
        String text = null;
        // try-with-resources
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String read;
            while (true) {
                read = br.readLine();
                if (read != null) {
                    text = read;
                } else {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return text;
    }

    public void writeScan(final String scan) {
        // TODO: Replace with actual folderFiles when integrating
        File file = new File(mContext.getFilesDir(), "scan");
        writeFile(scan, file);
    }

    public String readScan() {
        String scan;
        // TODO: Replace with actual folderFiles when integrating
        File file = new File(mContext.getFilesDir(), "scan");
        scan = readFile(file);
        if (scan == null) {
            // TODO: Replace with actual mScanTick when integrating
            scan = String.valueOf(1000); // Default value
            writeScan(scan);
        }
        return scan;
    }

    public void writeTick(final String tick) {
        // TODO: Replace with actual folderFiles when integrating
        File file = new File(mContext.getFilesDir(), "tick");
        writeFile(tick, file);
    }

    public String readTick() {
        String tick;
        // TODO: Replace with actual folderFiles when integrating
        File file = new File(mContext.getFilesDir(), "tick");
        tick = readFile(file);
        if (tick == null) {
            // TODO: Replace with actual mTick when integrating
            tick = String.valueOf(1000); // Default value
            writeTick(tick);
        }
        return tick;
    }

    public void writeInterval(final String interval) {
        File file = new File(mContext.getFilesDir(), "interval");
        writeFile(interval, file);
    }

    public String readInterval() {
        String interval;
        File file = new File(mContext.getFilesDir(), "interval");
        interval = readFile(file);
        if (interval == null) {
            interval = "100";
            writeInterval(interval);
        }
        return interval;
    }

    public int Count() {
        return AccountInformation.size();
    }

    public void Account(final String newAccount, final int sel) {
        int pos;
        if (sel < 0) {
            pos = mCurrentAcount;
        } else {
            pos = sel;
        }
        AccountInformation.get(pos).Account(newAccount);
    }

    public String Account(final int sel) {
        int pos;
        if (sel < 0) {
            pos = mCurrentAcount;
        } else {
            pos = sel;
        }
        return AccountInformation.get(pos).Account();
    }

    public void Password(final String newPassword, final int sel) {
        int pos;
        if (sel < 0) {
            pos = mCurrentAcount;
        } else {
            pos = sel;
        }
        AccountInformation.get(pos).Key(newPassword);
    }

    public String Password(final int sel) {
        int pos;
        if (sel < 0) {
            pos = mCurrentAcount;
        } else {
            pos = sel;
        }
        return AccountInformation.get(pos).Key();
    }

    public void writeAddress(final String address, final int sel) {
        int pos;
        if (sel < 0) {
            pos = mCurrentAcount;
        } else {
            pos = sel;
        }
        AccountInformation.get(pos).Logical(address);
    }

    public String getAddress(final int sel) {
        int pos;
        if (sel < 0) {
            pos = mCurrentAcount;
        } else {
            pos = sel;
        }
        return AccountInformation.get(pos).Logical();
    }

    public void writeRank(final String rank, final int sel) {
        int pos;
        if (sel < 0) {
            pos = mCurrentAcount;
        } else {
            pos = sel;
        }
        AccountInformation.get(pos).Rank(rank);
    }

    public String getRank(final int sel) {
        int pos;
        if (sel < 0) {
            pos = mCurrentAcount;
        } else {
            pos = sel;
        }
        return AccountInformation.get(pos).Rank();
    }

    private byte[] octetPassword() {
        return setStr2Oct(Password(-1));
    }

    private byte[] octetAddress() {
        return setStr2Oct(getAddress(-1));
    }

    private byte[] octetRank() {
        return setStr2Oct(getRank(-1));
    }

    public byte Addr() {
        byte[] r = octetAddress();
        return r[0];
    }

    public byte Rank() {
        byte[] r = octetRank();
        return r[0];
    }

    public void updateAllMeterInformation() {
        if (AccountInformation.size() > 1) {
            File file = new File(mContext.getFilesDir(), "meterinfo");
            try (FileWriter writer = new FileWriter(file, false)) {
                for (int i = 0; i < AccountInformation.size(); i++) {
                    writer.write(AccountInformation.get(i).get() + "\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public int addMeterInformation(final AccountInfo info) {
        File file = new File(mContext.getFilesDir(), "meterinfo");
        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(info.get() + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
        AccountInformation.add(info);
        return AccountInformation.size() - 1;
    }

    public void readMeterInformation() {
        AccountInformation.clear();
        File file = new File(mContext.getFilesDir(), "meterinfo");
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            while (true) {
                String read = br.readLine();
                if (read != null) {
                    AccountInfo temp = new AccountInfo(read);
                    AccountInformation.add(temp);
                } else {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (AccountInformation.size() == 0) {
            mCurrentAcount = addMeterInformation(new AccountInfo());
        }
    }

    public int findAccount(final String acount) {
        int find = 0;
        for (int i = 1; i < AccountInformation.size(); i++) {
            if (AccountInformation.get(i).Account().equals(acount)) {
                find = i;
            }
        }
        return find;
    }

    public boolean setCurrentAccount(final String account) {

        boolean find = false;

        mCurrentAcount = findAccount(account);
        if (mCurrentAcount != 0) {
            find = true;
        } else {
            AccountInfo temp = new AccountInfo(AccountInformation.get(0).get());
            temp.Account(account);
            mCurrentAcount = addMeterInformation(temp);
        }
        return find;
    }

    public void setCurrentLevel(final int level) {
        mCurrentAcount = level;
    }

    public void changeCurrent(final int pos) {
        mCurrentAcount = pos;
    }

    public int getCurrent() {
        return mCurrentAcount;
    }

    public String getAccountList() {
        StringBuffer ret = new StringBuffer();
        for (int i = 0; i < AccountInformation.size(); i++) {
            if (i > 0) {
                ret.append(",");
            }
            if (AccountInformation.get(i).Account().isEmpty()) {
                ret.append("Default");
            } else {
                ret.append(AccountInformation.get(i).Account());
            }
        }
        return ret.toString();
    }

    private final int CRC16(final byte[] buff, final int count) {
        int fcs16 = 0xFFFF;
        for (int pos = 1; pos < 1 + count; ++pos) {
            fcs16 = ((fcs16 >> 8)
                    ^ fcs16Table[(fcs16 ^ (int) buff[pos]) & 0xFF]) & 0xFFFF;
        }
        fcs16 = ~fcs16;
        fcs16 = ((fcs16 >> 8) & 0xFF) | (fcs16 << 8);
        return (fcs16 & 0xFFFF);
    }

    private int getAlert(ArrayList<String> out, final String in, final String[] alert) {
        byte eval = 1;
        int detect = 0;
        long tmp = Long.parseLong(in);
        byte[] hex = new byte[4];
        setUInt32(hex, 0, (int) tmp);
        for (int i = 0; i < 32; i++) {
            if ((i % 8) == 0) {
                eval = 1;
            }
            if ((hex[3 - (i / 8)] & eval) > 0) {
                out.add(format("  %s", alert[i]));
                detect++;
            }
            eval <<= 1;
        }
        return detect;
    }

    private Gson modelingData(ArrayList<String> out, final ArrayList<String> data) {
        Long val;
        Gson gson = new Gson();
        int idx;
        float f0, f1;
        ArrayList<String> tmp = new ArrayList<String>();
        switch (mObj) {
            case IST_APPROVAL_NO:
                out.add(format("Serial NO: %s", data.get(0)));
                break;
            case IST_RAM:
                out.add(format("Setting value is \"%s\"", data.get(0)));
                break;
            case IST_SERIAL_NO:
                out.add(format("Serial No: %s", data.get(0)));
                break;
            case IST_FIRM_VER:
                out.add(format("Revision No: %s", data.get(0)));
                out.add(format("Date: %s", data.get(1)));
                out.add(format("Time: %s", data.get(2)));
                out.add(format("Code: %s", data.get(3)));
                out.add(format("Year: %s", data.get(4)));
                out.add(format("Account: %s", data.get(5)));
                break;
            case IST_ALARM_DSC1:
                getAlert(out, data.get(0), alert1);
                if (out.size() == 0) {
                    out.add("No Alert");
                }
                break;
            case IST_ALARM_DSC2:
                getAlert(out, data.get(0), alert2);
                if (out.size() == 0) {
                    out.add("No Alert");
                }
                break;
            case IST_SETUP_PULS:
                out.add(format("Shift: %s", data.get(0)));
                out.add(format("FixedPlus: %s", data.get(1)));
                out.add(format("Source: %s", data.get(2)));
                out.add(format("Division1: %s", data.get(3)));
                out.add(format("Division2: %s", data.get(4)));
                out.add(format("Division3: %s", data.get(5)));
                out.add(format("Division4: %s", data.get(6)));
                break;

            case IST_SPECIFICATION:
                out.add(data.get(0));
                out.add(format("Serial NO.: %s", data.get(4)));
                out.add(format("Battery Lev: %s", data.get(9)));
                out.add(format("Potential  : %s", data.get(10)));
                out.add(format("Last status: %s", getBitsStr(data.get(11))));
                idx = Integer.parseInt(data.get(12));
                out.add(format("Last event : %s", PQCODE[idx]));
//              out.add(String.format("Fault: %s", data.get(13)));
                out.add(format("Alert1 Dsc : %s", data.get(14)));
                tmp.clear();
                getAlert(tmp, data.get(14), alert1);
                out.addAll(tmp);
                out.add(format("Alert2 Dsc : %s", data.get(15)));
                tmp.clear();
                getAlert(tmp, data.get(15), alert2);
                out.addAll(tmp);
                break;

            case IST_CHECK_MEASURE:
                out.add(data.get(0));
//              out.add(String.format("Stamp date: %s", data.get(7)));
//              out.add(String.format("Stamp date: %s", data.get(9)));
                out.add(format("Serial NO.: %s", data.get(1)));
                out.add(format("IMP: %.3f [kWh]", Float(1000.0, data.get(2))));
                out.add(format("EXP: %.3f [kWh]", Float(1000.0, data.get(3))));
                out.add(format("ABS: %.3f [kWh]", Float(1000.0, data.get(4))));
                out.add(format("NET: %.3f [kWh]", Float(1000.0, data.get(5))));
                out.add(format("Max Imp : %.3f [kW], Exp: %.3f [kW]", Float(1000.0, data.get(6)), Float(1000.0, data.get(8))));
                out.add(format("Inst Imp: %.3f [kW], Exp: %.3f [kW]", Float(1000.0, data.get(10)), Float(1000.0, data.get(11))));
                out.add(format("Volt0: %.2f [V], Min: %.2f [V]", Float(100.0, data.get(12)), Float(100.0, data.get(13))));
                out.add(format("Current L1: %.2f [A], L2: %.2f [A]", Float(100.0, data.get(14)), Float(100.0, data.get(15))));
                out.add(format("Power factor: %.2f ", Float(100.0, data.get(16))));
                out.add(format("Block Imp: %.3f [kW], Exp: %.3f [kW]", Float(1000.0, data.get(17)), Float(1000.0, data.get(18))));
                break;

            case IST_BILLING_PARAMS:
                ArrayList<BillingData> list = new ArrayList<BillingData>();
                for (int i = 0; i < data.size(); ) {
                    out.add(data.get(i));
                    out.add(format("IMP: %.3f [kWh], EXP: %.3f [kWh]", Float(1000.0, data.get(i + 1)), Float(1000.0, data.get(i + 2))));
                    out.add(format("ABS: %.3f [kWh], NET: %.3f [kWh]", Float(1000.0, data.get(i + 3)), Float(1000.0, data.get(i + 4))));
                    out.add(format("Max Imp: %.3f [kW], Exp: %.3f [kW]", Float(1000.0, data.get(i + 5)), Float(1000.0, data.get(i + 6))));
                    out.add(format("Volt0 Min: %.2f [V]", Float(100.0, data.get(i + 7))));
                    out.add(format("Alert1 Dsc: %s", getBitsStr(data.get(i + 8))));
                    out.add(format("Alert2 Dsc: %s", getBitsStr(data.get(i + 9))));
                    i += 10;
                }
                break;

            case IST_LOAD_PROFILE:
                for (int i = 0; i < data.size(); ) {
                    out.add(data.get(i++));
                    out.add(format("Status   : %s", getBitsStr(data.get(i++))));
                    out.add(format("Volt0 Ave: %.2f [V]", Float(100.0, data.get(i++))));
                    out.add(format("Block Imp: %.3f [kW], Exp: %.3f [kW]", Float(1000.0, data.get(i++)), Float(1000.0, data.get(i++))));
                }
                break;
            case IST_AMPR_RECORD:
                for (int i = 0; i < data.size(); ) {
                    out.add(data.get(i++));
                    out.add(format("Current L1 Ave: %.2f [A]", Float(100.0, data.get(i++))));
                }
                break;
            case IST_POWER_QUALITY:
                for (int i = 0; i < data.size(); ) {
                    out.add(data.get(i++));
                    idx = Integer.parseInt(data.get(i++));
                    out.add(format("Event: %s", PQCODE[idx]));
                    out.add(format("Volt0: %.2f [V]", Float(100.0, data.get(i++))));
                }
                break;
            case IST_METER_LOG:
                for (int i = 0; i < data.size(); ) {
                    long sec = Long.parseLong(data.get(i++));
                    byte[] code = setStr2Oct(data.get(i++));
                    idx = getUI8(code, 0);
                    int num = getUI8(code, 1);
                    if (idx > LOGCODE.length) {
                        idx = 0;
                    }
                    long day = sec / 86400;
                    sec %= 86400;
                    long hour = sec / 3600;
                    sec %= 3600;
                    long min = sec / 60;
                    sec %= 60;
                    out.add(format("%d Day %02d:%02d:%02d,%s,%d", day, hour, min, sec, LOGCODE[idx], num));
                }
                break;
            case IST_CAL_ENERGY:
            case IST_CAL_VOLTAMP:
                for (int i = 0; i < data.size(); i++) {
                    out.add(format("%04X", Long.parseLong(data.get(i))));
                }
                break;
            default:
                break;
        }
        return gson;
    }

    public String getNowDate(final boolean modeling) {
        final Long now = System.currentTimeMillis();
        final DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        final Date date = new Date(now);

        if (modeling)
            return "==== " + df.format(date) + " ====";
        else
            return df.format(date);
    }

    private String dataAccessResult(final byte[] in, final int offset) {
        String ret;
        int code = getUI8(in, offset);

        switch (code) {
            case 0:
                ret = "success (0)";
                break;
            case 1:
                ret = "hardware fault (1)";/**/
                break;
            case 2:
                ret = "temporary failure (2)";/**/
                break;
            case 3:
                ret = "read write denied (3)";/**/
                break;
            case 4:
                ret = "object undefined (4)";/**/
                break;
            case 9:
                ret = "object class inconsistent (9)";/**/
                break;
            case 11:
                ret = "object unavailable (11)";/**/
                break;
            case 12:
                ret = "type unmatched (12)";/**/
                break;
            case 13:
                ret = "scope of access violated (13)";/**/
                break;
            case 14:
                ret = "data block unavailable (14)";/**/
                break;
            case 15:
                ret = "long get aborted (15)";/**/
                break;
            case 16:
                ret = "no long get in progress (16)";/**/
                break;
            case 17:
                ret = "long set aborted (17)";/**/
                break;
            case 18:
                ret = "no long set in progress (18)";/**/
                break;
            case 19:
                ret = "data block number invalid (19)";/**/
                break;
            case 250:
                ret = "other reason(250)";/**/
                break;
            default:
                ret = format("unknown code (%d)", code);/**/
                break;
        }
        return ret;
    }

    private byte[] setTag(final byte id, final byte[] data) {
        byte[] out;
        int offset = 0, len;
        len = data.length;
        if (data.length > 65535) {
            offset = 6;
            out = new byte[len + offset];
            out[5] = (byte) (len & 0xff);
            len >>= 8;
            out[4] = (byte) (len & 0xff);
            len >>= 8;
            out[3] = (byte) (len & 0xff);
            len >>= 8;
            out[2] = (byte) (len & 0xff);
            out[1] = (byte) 0x84;
        } else {
            if (len > 255) {
                offset = 4;
                out = new byte[len + offset];
                out[3] = (byte) (len & 0xff);
                len >>= 8;
                out[2] = (byte) (len & 0xff);
                out[1] = (byte) 0x82;
            } else {
                if (data.length > 127) {
                    offset = 3;
                    out = new byte[len + offset];
                    out[2] = (byte) (len & 0xff);
                    out[1] = (byte) 0x81;
                } else {
                    offset = 2;
                    out = new byte[len + offset];
                    out[1] = (byte) (len & 0xff);
                }
            }
        }
        out[0] = id;
        System.arraycopy(data, 0, out, offset, data.length);
        return out;
    }

    private byte[] getTag(int[] inf, final byte[] in) {
        byte[] out;
        int len = 0;
        /*inf[0]:tag, inf[1]: offset*/
        inf[0] = in[inf[1]++];  /*tag*/
        if ((in[inf[1]] & 0x80) > 0) {
            int cnt = getUI8(in, inf[1]++);
            cnt &= 0x7f;
            for (int i = 0; i < cnt; i++) {
                len <<= 8;
                len += getUI8(in, inf[1]++);
            }
        } else {
            len = getUI8(in, inf[1]++);
        }
        if ((len + inf[1]) > in.length) {
            return null;
        }
        out = new byte[len];
        System.arraycopy(in, inf[1], out, 0, len);
        inf[1] += len;
        return out;
    }

    private void getCount(int[] io, final byte[] in) {
        /*io[0]:offset,io[1]:count*/
        byte tmp = in[io[0]];
        if ((tmp & 0x80) > 0) {
            int cnt = tmp & 0x7f;
            io[0]++;
            io[1] = 0;
            for (int i = 0; i < cnt; i++) {
                io[1] <<= 8;
                io[1] += getUI8(in, io[0]++);
            }
        } else {
            io[1] = getUI8(in, io[0]++);
        }
    }

    private void getData(ArrayList<String> data, int[] io, final byte[] in) {
        byte[] buff;
        long val;
        int i, type, count;
        int year, mon, day, hour, min, sec;

        if (in.length > 0) {
            type = in[io[0]++];
            switch (type) {
                case 0:      //"null_data"
                    break;
                case 1:      //"array"
                case 2:      //"structure"
                    getCount(io, in);
                    count = io[1];
                    for (i = 0; (i < count) && (io[0] < in.length); i++) {
                        getData(data, io, in);
                    }
                    break;
                case 3:      //"boolean"
                    val = getUI8(in, io[0]);
                    if (val > 0) {
                        data.add("true");
                    } else {
                        data.add("false");
                    }
                    io[0]++;
                    break;
                case 4:      //"bit_string"
                    break;
                case 5:      //"double_long"
                    val = getI32(in, io[0]);
                    data.add(format("%d", val));
                    io[0] += 4;
                    break;
                case 6:        //"double_long_unsigned"
                    val = getUI32(in, io[0]);
                    data.add(format("%d", val));
                    io[0] += 4;
                    break;
                case 7:        //"floating_point"
                    break;
                case 9:        //"octet_string"
                    getCount(io, in);
                    if (io[1] == 12) {  /*サイズで強制的に日時に変換*/
                        year = getUI16(in, io[0]);
                        io[0] += 2;
                        mon = getUI8(in, io[0]);
                        io[0]++;
                        day = getUI8(in, io[0]);
                        io[0]++;
                        io[0]++;//day of week
                        hour = getUI8(in, io[0]);
                        io[0]++;
                        min = getUI8(in, io[0]);
                        io[0]++;
                        sec = getUI8(in, io[0]);
                        io[0]++;
                        io[0] += 4;
                        data.add(format("%04d/%02d/%02d %02d:%02d:%02d", year, mon, day, hour, min, sec));
                    } else {
                        data.add(setOct2Str(in, io[0], io[1]));
                        io[0] += io[1];
                    }
                    break;
                case 10:    //"visible_string"
                    getCount(io, in);
                    data.add(setStr2Str(in, io[0], io[1]));
                    io[0] += io[1];
                    break;
                case 13:    //"bcd"
                    break;
                case 15:    //"integer"
                    data.add(format("%d", getI8(in, io[0])));
                    io[0] += 1;
                    break;
                case 16:    //"long"
                    data.add(format("%d", getI16(in, io[0])));
                    io[0] += 2;
                    break;
                case 17:    //"unsigned"
                case 22:    //"enum"
                    data.add(format("%d", getUI8(in, io[0])));
                    io[0] += 1;
                    break;
                case 18:    //"long_unsigned"
                    data.add(format("%d", getUI16(in, io[0])));
                    io[0] += 2;
                    break;
                case 19:    //"compact_array"
                    break;
                case 20:    //"long64"
                    break;
                case 21:    //"long64_unsigned"
                    break;
                case 23:    //"float32"
                    io[0] += 4;
                    break;
                case 24:    //"float64"
                    io[0] += 8;
                    break;
                case 25:    //"date_time"
                    year = getUI16(in, io[0]);
                    io[0] += 2;
                    mon = getUI8(in, io[0]);
                    io[0]++;
                    day = getUI8(in, io[0]);
                    io[0]++;
                    io[0]++;//day of week
                    hour = getUI8(in, io[0]);
                    io[0]++;
                    min = getUI8(in, io[0]);
                    io[0]++;
                    sec = getUI8(in, io[0]);
                    io[0]++;
                    io[0] += 4;
                    data.add(format("%04d/%02d/%02d %02d:%02d:%02d", year, mon, day, hour, min, sec));
                    break;
                case 26:    //"date"
                    year = getUI16(in, io[0]);
                    io[0] += 2;
                    mon = getUI8(in, io[0]);
                    io[0]++;
                    day = getUI8(in, io[0]);
                    io[0]++;
                    io[0]++;//day of week
                    data.add(format("%04d/%02d/%02d", year, mon, day));
                    break;
                case 27:    //"time"
                    hour = getUI8(in, io[0]);
                    io[0]++;
                    min = getUI8(in, io[0]);
                    io[0]++;
                    sec = getUI8(in, io[0]);
                    io[0]++;
                    io[0] += 4;
                    data.add(format("%02d:%02d:%02d", hour, min, sec));
                    break;
                case 255:    //"don't_care"
                    break;
            }
        }
    }

    public void init(final byte sizes, final byte sizer,
                     final byte wins, final byte winr) {
        if (info != null) {
            info = null;
        }
        info = new byte[def_info.length];
        System.arraycopy(def_info, 0, info, 0, def_info.length);
        info[5] = sizes;
        info[8] = sizer;
        info[11] = wins;
        info[14] = winr;
    }

    private byte[] hdlcs(final byte cmd, final byte[] llc) {
        int crc, len, offset;
        byte tmp;
        byte[] s;

        len = 7;
        if (llc != null) {
            len += llc.length;
            len += 2 + 3;/*crc+llc headder*/
        }
        s = new byte[len + 2];

        offset = 0;
        s[offset++] = (byte) 0x7e;
        s[offset] = (byte) 0xa0;
        tmp = (byte) (len >> 8);
        s[offset++] += (byte) (tmp & 0x0f);
        s[offset++] = (byte) (len & 0xff);
        s[offset++] = adr1[0];
        s[offset++] = Addr();
        s[offset++] = cmd;
        crc = CRC16(s, offset - 1);
        offset = setUInt16(s, offset, crc);
        if (llc != null) {
            s[offset++] = (byte) 0xe6;
            s[offset++] = (byte) 0xe6;
            s[offset++] = (byte) 0x00;
            System.arraycopy(llc, 0, s, offset, llc.length);
            offset += llc.length;
            crc = CRC16(s, offset - 1);
            offset = setUInt16(s, offset, crc);
        }
        s[offset] = (byte) 0x7e;
        return s;
    }

    private byte[] hdlcr(int[] ret, final byte[] in) {
        int crc, len;
        byte tmp;

        ret[0] = 0;
        ret[1] = 0;
        if (in.length >= 9) {
            //in[offset]=(byte)0x7e;
            ret[1]++;
            len = getUI8(in, ret[1]++);
            len &= 0x0f;
            len <<= 8;
            len += getUI8(in, ret[1]++);
            if (in.length == (len + ret[1] - 1)) {
                if (in[ret[1]++] == Addr()) {
                    if (in[ret[1]++] == adr1[0]) {
                        ret[0] = in[ret[1]++];
                        crc = CRC16(in, ret[1] - 1);
                        tmp = (byte) (crc >> 8);
                        if (in[ret[1]++] == tmp) {
                            if (in[ret[1]++] == (byte) (crc & 0xff)) {
                                if (len > 12) {
                                    len -= 12;
                                    ret[1] += 3;//llc header
                                    byte[] llc = new byte[len];
                                    System.arraycopy(in, ret[1], llc, 0, len);
                                    ret[1] += len;
                                    crc = CRC16(in, ret[1] - 1);
                                    tmp = (byte) (crc >> 8);
                                    if (in[ret[1]++] == tmp) {
                                        if (in[ret[1]] == (byte) (crc & 0xff)) {
                                            return llc;
                                        } else {
                                            ret[0] = 0;
                                        }
                                    } else {
                                        ret[0] = 0;
                                    }
                                } else {
                                    /*UA?*/
                                }
                            } else {
                                ret[0] = 0;
                            }
                        } else {
                            ret[0] = 0;
                        }
                    }
                }
            }
        }
        return null;
    }

    private void setClientAppTitle(final byte[] in, final int offset) {
        if (this.clAppTitle != null) {
            this.clAppTitle = null;
        }
        this.clAppTitle = new byte[8];
        System.arraycopy(in, offset, clAppTitle, 0, 8);
    }

    private void setServerAppTitle(final byte[] in, final int offset) {
        if (this.svAppTitle != null) {
            this.svAppTitle = null;
        }
        this.svAppTitle = new byte[8];
        System.arraycopy(in, offset, svAppTitle, 0, 8);
    }

    private void setClientChallenge(final byte[] in, final int offset) {
        if (this.clChallenge != null) {
            this.clChallenge = null;
        }
        this.clChallenge = new byte[31 + 1];
        System.arraycopy(in, offset, clChallenge, 1, 31);
    }

    private void setServerChallenge(final byte[] in, final int offset) {
        if (this.svChallenge != null) {
            this.svChallenge = null;
        }
        this.svChallenge = new byte[31 + 1];
        System.arraycopy(in, offset, svChallenge, 1, 31);
    }

    private byte[] encrypt(final byte[] keyData, final byte sc, final byte[] in) {

        byte[] iv;
        byte[] out;

        out = new byte[5 + in.length + 12];
        Cipher c = null;
        SecretKeySpec key = new SecretKeySpec(keyData, "AES");
        // AESアルゴリズムでCipherオブジェクトを作成
        try {
            c = Cipher.getInstance("AES/GCM/NoPadding");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        }
        out[0] = sc;
        FrameCounter(out, 1);

        // Cipherオブジェクトに秘密鍵を設定
        iv = new byte[clAppTitle.length + 4];
        System.arraycopy(clAppTitle, 0, iv, 0, clAppTitle.length);
        System.arraycopy(out, 1, iv, clAppTitle.length, 4);
        try {
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(12 * 8, iv));
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
        if (out[0] == 0x10) {
            svChallenge[0] = out[0];
            c.updateAAD(svChallenge);
        } else {
            clChallenge[0] = out[0];
            c.updateAAD(clChallenge);
        }
        // 暗号化
        byte[] enc = null;
        try {
            enc = c.doFinal(in);
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        }
        System.arraycopy(enc, 0, out, 5, enc.length);
        return out;
    }

    private byte[] decrypt(final byte[] keyData, final byte[] in) {

        byte[] iv;
        Cipher c = null;
        SecretKeySpec key = new SecretKeySpec(keyData, "AES");
        // AESアルゴリズムでCipherオブジェクトを作成
        try {
            c = Cipher.getInstance("AES/GCM/NoPadding");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        }
// Cipherオブジェクトに秘密鍵を設定
        iv = new byte[svAppTitle.length + 4];
        System.arraycopy(svAppTitle, 0, iv, 0, svAppTitle.length);
        System.arraycopy(in, 1, iv, svAppTitle.length, 4);
        try {
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(12 * 8, iv));
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        }
        if (in[0] == 0x10) {
            clChallenge[0] = in[0];
            c.updateAAD(clChallenge);
        } else {
            svChallenge[0] = in[0];
            c.updateAAD(svChallenge);
        }
        //復号
        byte[] out = null;
        try {
            out = c.doFinal(in, 5, in.length - 5);
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        }
        return out;
    }

    private int setApp1() {

        int len = def_app1.length;

        if (app1 == null) {
            app1 = new byte[def_app1.length];
        }
        System.arraycopy(def_app1, 0, app1, 0, def_app1.length);
        switch (mRank) {
            case RANK_SUPER:
            case RANK_ADMIN:
                app1[app1.length - 1] = 3;
                break;
            default:
                app1[app1.length - 1] = 1;
                break;
        }
        return len;
    }

    private int setApp6() {

        int len = def_app6.length;
        byte[] acount = Account(-1).getBytes();
        switch (mRank) {
            case RANK_SUPER:
            case RANK_ADMIN:
                if (app6 == null) {
                    app6 = new byte[def_app6.length];
                }
                System.arraycopy(def_app6, 0, app6, 0, def_app6.length);
                System.arraycopy(acount, 0, app6, 4, acount.length);
                break;
            default:
                app6 = null;
                len = 0;
                break;
        }
        return len;
    }

    private int setApp10() {

        int len = def_app10.length;
        switch (mRank) {
            case RANK_SUPER:
            case RANK_ADMIN:
            case RANK_READER:
                if (app10 == null) {
                    app10 = new byte[def_app10.length];
                }
                System.arraycopy(def_app10, 0, app10, 0, def_app10.length);
                break;
            default:
                app10 = null;
                len = 0;
                break;
        }
        return len;
    }

    private int setApp11() {

        int len = def_app11.length;

        switch (mRank) {
            case RANK_SUPER:
            case RANK_ADMIN:
                if (app11 == null) {
                    app11 = new byte[def_app11.length];
                }
                System.arraycopy(def_app11, 0, app11, 0, def_app11.length);
                app11[app11.length - 1] = 5;
                break;
            case RANK_POWER:
            case RANK_READER:
                if (app11 == null) {
                    app11 = new byte[def_app11.length];
                }
                System.arraycopy(def_app11, 0, app11, 0, def_app11.length);
                app11[app11.length - 1] = 1;
                break;
            default:
                app11 = null;
                len = 0;
                break;
        }
        return len;
    }

    private int setApp12() {

        int len;
        byte[] octet;
        switch (mRank) {
            case RANK_SUPER:
            case RANK_ADMIN:
                int remain = challenge0.length;
                int step = 8;
                Random random = new Random(seed1);
                for (int i = 0; i < challenge0.length; i += step) {
                    if (remain < 8) {
                        step = remain;
                    }
                    remain -= step;
                    seed1 = random.nextLong();
                    System.arraycopy(ByteBuffer.allocate(8).putLong(seed1).array(), 0, challenge0, i, step);
                }
                octet = setTag((byte) 0x80, challenge0);
                app12 = setTag(def_app12, octet);
                len = app12.length;
                break;

            case RANK_POWER:
            case RANK_READER:
                octet = setTag((byte) 0x80, octetPassword());
                app12 = setTag(def_app12, octet);
                len = app12.length;
                break;

            default:
                app12 = null;
                len = 0;
                break;
        }
        return len;
    }

    private int setApp30() {
        app30 = null;
        byte[] octet;
        byte[] userinfo;

        switch (mRank) {
            case RANK_SUPER:
            case RANK_ADMIN:
                global = octetPassword();
                if (dedicate == null) {
                    dedicate = new byte[global.length];
                }
                Random random = new Random(seed2);
                for (int i = 0; i < dedicate.length; i += 8) {
                    seed2 = random.nextLong();
                    System.arraycopy(ByteBuffer.allocate(8).putLong(seed2).array(), 0, dedicate, i, 8);
                }
                setClientAppTitle(app6, 4);
                setClientChallenge(app12, 4);
                byte[] _initQ = new byte[def_conf.length + dedicate.length + 3];
                _initQ[0] = 0x01;
                _initQ[1] = 0x01; /*ded*/
                _initQ[2] = (byte) dedicate.length;
                System.arraycopy(dedicate, 0, _initQ, 3, dedicate.length);
                System.arraycopy(def_conf, 0, _initQ, 3 + dedicate.length, def_conf.length);
                byte[] initQ = encrypt(global, (byte) 0x30, _initQ);
                octet = setTag(glo_iniQ, initQ);
                break;

            default:
                octet = new byte[def_conf.length + 2];
                octet[0] = 0x01;
                octet[1] = 0x00;/*ded*/
                System.arraycopy(def_conf, 0, octet, 2, def_conf.length);
                break;
        }
        userinfo = setTag((byte) 0x04, octet);
        app30 = setTag(def_app30, userinfo);
        return app30.length;
    }

    private void FrameCounter(byte[] out, final int offset) {
        Random random = new Random(seed0);
        seed0 = random.nextInt();
        System.arraycopy(ByteBuffer.allocate(4).putInt(seed0).array(), 0, out, offset, 4);
    }

    public byte[] Release() {
        byte[] data = new byte[def_rlrq.length];
        System.arraycopy(def_rlrq, 0, data, 0, def_rlrq.length);
        return hdlcs((byte) 0x13, setTag(RLRQ, data));
    }

    public byte[] Close(int[] ret, final byte[] res) {

        byte[] receive = null;

        receive = hdlcr(ret, res);
        if (0 == ret[0]) {
            ret[1] = -2;
            return null;
        }
        if (receive[4] != 0) {
            ret[0] = 0;
            ret[1] = -1;
            return null;
        }
        return hdlcs((byte) 0x53, null);
    }

    public void Finish(int[] ret, final byte[] res) {

        hdlcr(ret, res);
        if (0 == ret[0]) {
            ret[1] = -2;
        }
    }

    public byte[] Open() {
        ns = 0;
        nr = 0;
        mRank = Rank();
        if (info != null) {
            info = null;
        }
        return hdlcs((byte) 0x93, null);
    }

    public byte[] Session(int[] ret, final byte[] res) {

        byte[] receive = null;
        mBlockNo = 0;

        hdlcr(ret, res);
        if (0 == ret[0]) {
            ret[1] = -2;
            return null;
        }
        int len = 0, offset;
        len += setApp1();
        len += setApp6();
        len += setApp10();
        len += setApp11();
        len += setApp12();
        len += setApp30();

        offset = 0;
        byte[] data = new byte[len];
        if (app1 != null) {
            System.arraycopy(app1, 0, data, offset, app1.length);
            offset += app1.length;
        }
        if (app6 != null) {
            System.arraycopy(app6, 0, data, offset, app6.length);
            offset += app6.length;
        }
        if (app10 != null) {
            System.arraycopy(app10, 0, data, offset, app10.length);
            offset += app10.length;
        }
        if (app11 != null) {
            System.arraycopy(app11, 0, data, offset, app11.length);
            offset += app11.length;
        }
        if (app12 != null) {
            System.arraycopy(app12, 0, data, offset, app12.length);
            offset += app12.length;
        }
        if (app30 != null) {
            System.arraycopy(app30, 0, data, offset, app30.length);
            offset += app30.length;
        }
        return hdlcs((byte) 0x13, setTag(AARQ, data));
    }

    public byte[] Challenge(int[] ret, final byte[] res) {
        byte id = 0;
        byte[] llc = null;
        byte[] initR = null;

        llc = hdlcr(ret, res);
        if (0 == ret[0]) {
            ret[1] = -2;
            return null;
        }
        boolean ok = true;
        boolean hls = false;
        boolean title = false;
        boolean value = false;
        ret[0] = 0;
        ret[1] = 0;
        llc = getTag(ret, llc);
        if (ret[0] != AARE) {
            return null;
        }
        ret[0] = 0;
        ret[1] = 0;
        for (ret[1] = 0; ret[1] < llc.length && ok; ) {
            byte[] app = null;
            app = getTag(ret, llc);
            ok = false;
            if (app != null) {
                id = (byte) (ret[0] & 0x00ff);
                switch (id) {
                    case (byte) 0xa1:
                        if (app.length == 9) {
                            hls = app[8] == 0x03;
                            ok = true;
                        }
                        break;
                    case (byte) 0xa2:
                        if (app.length == 3) {
                            ok = app[2] == 0x00;
                        }
                        break;
                    case (byte) 0xa3:
                        if (app.length == 5) {
                            if (hls) {
                                ok = app[4] == 0x0e;
                            } else {
                                ok = app[4] == 0x00;
                            }
                        }
                        break;
                    case (byte) 0xa4:
                        if (app.length == 10) {
                            setServerAppTitle(app, 2);
                            title = true;
                            ok = true;
                        }
                        break;
                    case (byte) 0x88:
                        if (app.length == 2) {
                            ok = true;
                        }
                        break;
                    case (byte) 0x89:
                        if (app.length == 7) {
                            ok = true;
                        }
                        break;
                    case (byte) 0xaa:
                        if (app.length == 0x21) {
                            setServerChallenge(app, 2);
                            value = true;
                            ok = true;
                        }
                        break;
                    case (byte) 0xbe:
                        if (app.length > 0) {
                            int[] inf = new int[2];
                            if (hls) {
                                inf[0] = 0;
                                inf[1] = 2;
                                if (title && value) {
                                    byte[] _initR = getTag(inf, app);
                                    if (inf[0] == 0x28) {
                                        initR = decrypt(global, _initR);
                                        ok = initR[0] == 0x08;
                                    }
                                }
                            } else {
                                inf[0] = 0;
                                inf[1] = 0;
                                initR = getTag(inf, app);
                                ok = initR[0] == 0x08;
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
        }
        if (!ok) {
            ret[0] = 0;
            return null;
        } else {
            ret[0] = 1;
        }
        if (hls) {
            byte[] gmac = new byte[0];
            gmac = encrypt(global, (byte) 0x10, gmac);

            byte[] actQ = new byte[ACTRQ.length + 3 + gmac.length];/*010911 + SC + FC + GMAC*/
            System.arraycopy(ACTRQ, 0, actQ, 0, ACTRQ.length);
            System.arraycopy(g_ist[IST_ASSO_LN2], 0, actQ, 4, 7);
            actQ[11] = 0x01;  /*method*/
            actQ[12] = 0x01;  /*data = true*/
            actQ[13] = 0x09;  /*octet*/
            actQ[14] = 0x11;  /*length*/
            System.arraycopy(gmac, 0, actQ, 15, gmac.length);
            byte[] data = encrypt(global, (byte) 0x30, actQ);
            return hdlcs((byte) 0x13, setTag(glo_actQ, data));
        } else {
            return null;
        }
    }

    public byte[] Confirm(int[] ret, final byte[] res) {

        byte[] llc = hdlcr(ret, res);
        if (0 == ret[0]) {
            ret[1] = -2;
            return null;
        }

        if (llc[0] == (byte) 0xcf) {
            ret[0] = 0;
            ret[1] = 0;
            byte[] actQ = getTag(ret, llc);
            actQ = decrypt(global, actQ);
            if (actQ != null) {
                ret[0] = 1;
                return null;
            }
        }
        return null;
    }

    public byte[] getReq(final int idx, final byte atr, final byte sel, final String attach, final byte pos) {

        int len, offset;
        byte[] getQ;
        byte[] param;
        if (attach != null) {
            param = setStr2Oct(attach);
        } else {
            param = new byte[0];
        }

        if (mBlockNo == 0) {
            mObj = idx;
            mMode = 0;
            mAtr = atr;
            mSel = sel;
            len = GETRQ.length;
            if (sel > 0) {
                len++;
            }
            if (param.length > 0) {
                len += param.length;
            }
            getQ = new byte[len];
            System.arraycopy(GETRQ, 0, getQ, 0, GETRQ.length);
            if (pos == 0) {
                System.arraycopy(g_ist[idx], 0, getQ, 4, 7);
            } else {
                System.arraycopy(g_ist[idx], 0, getQ, 4, 6);
                getQ[10] = pos;
            }
            getQ[11] = atr;
            if (sel > 0) {
                offset = GETRQ.length - 1;
                getQ[offset++] = (byte) 0x01;
                getQ[offset++] = sel;
            } else {
                offset = GETRQ.length;
            }
            if (param.length > 0) {
                System.arraycopy(param, 0, getQ, offset, param.length);
            }
        } else {
            len = GTNRQ.length;
            getQ = new byte[len];
            System.arraycopy(GTNRQ, 0, getQ, 0, len);
            setUInt32(getQ, len - 4, mBlockNo);
        }
        byte[] data;
        if (mRank == RANK_ADMIN || mRank == RANK_SUPER) {
            data = encrypt(dedicate, (byte) 0x30, getQ);
            data = hdlcs((byte) 0x13, setTag(ded_getQ, data));
        } else {
            data = hdlcs((byte) 0x13, getQ);
        }
        return data;
    }

    public byte[] setReq(final int idx, final byte atr, final byte sel, final String attach, final byte pos) {

        int len, offset;
        byte[] param;
        if (attach != null) {
            param = setStr2Oct(attach);
        } else {
            param = new byte[0];
        }

        len = SETRQ.length;
        if (sel > 0) {
            len++;
        }
        if (param.length > 0) {
            len += param.length;
        }
        byte[] setQ = new byte[len];
        System.arraycopy(SETRQ, 0, setQ, 0, SETRQ.length);
        if (pos == 0) {
            System.arraycopy(g_ist[idx], 0, setQ, 4, 7);
        } else {
            System.arraycopy(g_ist[idx], 0, setQ, 4, 6);
            setQ[10] = pos;
        }

        setQ[11] = atr;
        if (sel > 0) {
            offset = SETRQ.length - 1;
            setQ[offset++] = (byte) 0x01;
            setQ[offset++] = sel;
        } else {
            offset = SETRQ.length;
        }
        if (param.length > 0) {
            System.arraycopy(param, 0, setQ, offset, param.length);
        }
        byte[] data;
        if (mRank == RANK_ADMIN || mRank == RANK_SUPER) {
            data = encrypt(dedicate, (byte) 0x30, setQ);
            data = hdlcs((byte) 0x13, setTag(ded_setQ, data));
        } else {
            data = hdlcs((byte) 0x13, setQ);
        }
        mObj = idx;
        mMode = 1;
        mAtr = atr;
        mSel = sel;
        return data;
    }

    public byte[] actReq(final int idx, final byte mth, final String attach, final byte pos) {
        int len, offset;
        byte[] param;

        if (attach != null) {
            param = setStr2Oct(attach);
        } else {
            param = new byte[0];
        }

        len = ACTRQ.length;
        if (param.length > 0) {
            len++;
            len += param.length;
        }
        byte[] actQ = new byte[len];
        System.arraycopy(ACTRQ, 0, actQ, 0, ACTRQ.length);
        if (pos == 0) {
            System.arraycopy(g_ist[idx], 0, actQ, 4, 7);
        } else {
            System.arraycopy(g_ist[idx], 0, actQ, 4, 6);
            actQ[10] = pos;
        }
        actQ[11] = mth;
        offset = ACTRQ.length;
        if (param.length > 0) {
            actQ[offset++] = (byte) 0x01;
            System.arraycopy(param, 0, actQ, offset, param.length);
        }
        byte[] data;
        if (mRank == RANK_ADMIN || mRank == RANK_SUPER) {
            data = encrypt(dedicate, (byte) 0x30, actQ);
            data = hdlcs((byte) 0x13, setTag(ded_actQ, data));
        } else {
            data = hdlcs((byte) 0x13, actQ);
        }
        mObj = idx;
        mMode = 3;
        mAtr = mth;
        mSel = 0;
        return data;
    }

    public ArrayList<String> DataRes(int[] ret, final byte[] in, final boolean modeling) {
        ArrayList<String> data = new ArrayList<String>();
        ArrayList<String> out = new ArrayList<String>();
        ret[0] = 0;
        ret[1] = 0;

        int[] len = new int[2];
        len[0] = 0;
        len[1] = 0;

        byte[] llc = hdlcr(len, in);
        if (0 == len[0]) {
            ret[1] = -2;
            mBlockNo = 0;
//          out.add(String.format("Fatal error: Invalid HDLC frame: %s", setOct2Str(in, 0, in.length)));
            return out;
        }
        if (llc[0] == 0x0e) {
            ret[1] = -1;
//            out.add(String.format("Confirm service error: %d,%d,%d", getUI8(llc, 1), getUI8(llc, 2), getUI8(llc, 3)));
            return out;
        }
        byte[] _res;
        if (mRank == RANK_ADMIN || mRank == RANK_SUPER) {
            len[0] = 0;
            len[1] = 0;
            _res = getTag(len, llc);
            _res = decrypt(dedicate, _res);
            if (_res == null) {
                ret[1] = -1;
                mBlockNo = 0;
//                out.add(String.format("Fatal error: fail to Decrypt frame: %s", setOct2Str(in, 0, in.length)));
                return out;
            }
        } else {
            _res = llc;
        }
        switch (mMode) {
            case 0: /*get*/
                if (_res.length > 3) {
                    int[] io = new int[2];
                    if (_res[1] == 1) {    /*normal*/
                        /*get:3, 4*/
                        if (_res[3] == 0x00) {
                            out.add(getNowDate(modeling));
                            io[0] = 0;
                            io[1] = 0;
                            byte[] app = new byte[_res.length - 4];
                            System.arraycopy(_res, 4, app, 0, _res.length - 4);
                            getData(data, io, app);
                            if (modeling) {
                                modelingData(out, data);
                            } else {
                                out.addAll(data);
                            }
                        } else {
                            out.add(dataAccessResult(_res, 4));
                            ret[1] = _res[4];
                            /*error*/
                        }
                        mBlockNo = 0;
                    } else {   /*Block*/
                        int no = 0, size;
                        no = (int) getUI32(_res, 4);    /*send block no*/
                        len[0] = 8;
                        len[1] = 0;
                        if (_res[len[0]] == 0) {
                            len[0]++; /*RAW 0*/
                            getCount(len, _res); /*raw size*/
                            size = len[1];
                            if (mBlockNo == 0) {
                                out.add(getNowDate(modeling));
                                len[0]++;/* ARRAY */
                                getCount(len, _res); /*record count*/
                            }
                            mBlockNo = no;
                            len[1] = _res.length - len[0];
                            byte[] app = new byte[len[1]];
                            System.arraycopy(_res, len[0], app, 0, len[1]);
                            for (io[0] = 0, io[1] = 0; io[0] < len[1]; ) {
                                getData(data, io, app);
                            }
                            if (modeling) {
                                modelingData(out, data);
                            } else {
                                out.addAll(data);
                            }
                            if (_res[3] == 0) {
                                ret[0] = 2; /*continue*/
                            } else {
                                ret[0] = 0;
                                mBlockNo = 0;
                            }
                        } else {
                            out.add(dataAccessResult(_res, len[0] + 1));
                            ret[1] = _res[len[0] + 1];
                            mBlockNo = 0;
                        }
                    }
                }
                break;
            case 1:/*set*/
                out.add(getNowDate(modeling));
                out.add(dataAccessResult(_res, 3));
                break;
            case 3:/*act*/
                out.add(getNowDate(modeling));
                out.add(dataAccessResult(_res, 3));
                ret[1] = _res[3];
                mBlockNo = 0;
                break;
            default:
                break;
        }
        return out;
    }

    public class AccountInfo {
        private final String[] mProperty = {null, null, null, null};

        public AccountInfo() {
        }

        public AccountInfo(final String in) {
            set(in);
        }

        public AccountInfo(final AccountInfo in) {
            set(in.get());
        }

        public boolean set(final String in) {
            boolean ret = false;

            String data;
            if (in != null) {
                if (in.isEmpty()) {
                    data = "Reader  ,3030303030303030,41,03";
                } else {
                    data = in;
                }
            } else {
                data = "Reader  ,3030303030303030,41,03";
            }
            String[] cells = data.split(",");
            if (cells.length == mProperty.length) {
                for (int i = 0; i < mProperty.length; i++) {
                    if (i < cells.length) {
                        mProperty[i] = cells[i];
                    }
                }
                ret = true;
            }
            return ret;
        }

        public boolean set(final String in1, final String in2, final String in3, final String in4) {
            mProperty[0] = in1;
            mProperty[1] = in2;
            mProperty[2] = in3;
            mProperty[3] = in4;
            return true;
        }

        public final String get() {
            return format("%s,%s,%s,%s",
                    mProperty[0], mProperty[1], mProperty[2], mProperty[3]);
        }

        public final String Account() {
            return mProperty[0];
        }

        public void Account(final String in) {
            mProperty[0] = in;
        }

        public final String Key() {
            return mProperty[1];
        }

        public void Key(final String in) {
            mProperty[1] = in;
        }

        public final String Logical() {
            return mProperty[2];
        }

        public void Logical(final String in) {
            mProperty[2] = in;
        }

        public final String Rank() {
            return mProperty[3];
        }

        public void Rank(final String in) {
            mProperty[3] = in;
        }
    }

    private class BillingData {
        public String Date;
        public String IMP;
        public String EXP;
        public String ABS;
        public String NET;
        public String Max_Imp;
        public String Max_Exp;
        public String Min_Volt0;
        public String Alert1_Dsc;
        public String Alert2_Dsc;

        BillingData(final String a, final String b, final String c, final String d, final String e, final String f, final String g, final String h, final String i, final String j) {
            Date = a;
            IMP = b;
            EXP = c;
            ABS = d;
            NET = e;
            Max_Imp = f;
            Max_Exp = g;
            Min_Volt0 = h;
            Alert1_Dsc = i;
            Alert2_Dsc = j;
        }
    }

    // TODO: Add AccSecurity class definition here when implementing HLS
    // For now, creating a placeholder class to prevent compilation errors
    private class AccSecurity {
        private final Context context;
        private final byte[] clientTitle;
        private final byte[] serverTitle;
        private SecretKey sessionKey;

        public AccSecurity(Context ctx, byte[] client, byte[] server) {
            this.context = ctx;
            this.clientTitle = client;
            this.serverTitle = server;
        }

        public SecretKey getSecretKeyFromKeystore(String alias) {
            // TODO: Implement keystore retrieval
            return null;
        }

        public SecretKey generateAndStoreAesGcmKey(String alias) {
            // TODO: Implement key generation and storage
            return null;
        }

        public SecretKey getSessionEncryptionKey() {
            return this.sessionKey;
        }

        public void setSessionEncryptionKey(SecretKey key) {
            this.sessionKey = key;
        }

        public long getNextClientInvocationCounterAndIncrement() {
            // TODO: Implement frame counter management
            return System.currentTimeMillis() & 0xFFFFFFFFL;
        }

        public byte[] encryptApdu(byte[] pdu, SecretKey key, byte[] title, long counter) {
            // TODO: Implement APDU encryption
            return pdu; // Placeholder
        }

        public byte[] decryptApdu(byte[] encrypted, SecretKey key, byte[] title, long counter) {
            // TODO: Implement APDU decryption
            return encrypted; // Placeholder
        }

        public void resetSecurityContext() {
            // TODO: Implement security context reset
        }
    }
}


