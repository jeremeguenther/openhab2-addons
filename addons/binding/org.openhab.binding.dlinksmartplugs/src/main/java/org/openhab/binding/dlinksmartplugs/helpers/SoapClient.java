/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.dlinksmartplugs.helpers;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.openhab.binding.dlinksmartplugs.handler.DLinkSmartPlugsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class SoapClient {

    private Logger logger = LoggerFactory.getLogger(DLinkSmartPlugsHandler.class);

    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder DOMParser = null;

    private String HNAP1_XMLNS = "http://purenetworks.com/HNAP1/";
    private String HNAP_METHOD = "POST";
    // private String HNAP_BODY_ENCODING = "UTF8";
    private String HNAP_LOGIN_METHOD = "Login";

    private VARS_HNAP_AUTH HNAP_AUTH = new VARS_HNAP_AUTH();

    public String Login(String user, String password, String url) {
        HNAP_AUTH.User = user;
        HNAP_AUTH.Pwd = password;
        HNAP_AUTH.URL = url;

        try {
            DOMParser = factory.newDocumentBuilder();

            String response = sendPost(HNAP_METHOD, HNAP_AUTH.URL, new String[] { "Content-Type", "SOAPAction" },
                    new String[] { "text/xml; charset=utf-8", '"' + HNAP1_XMLNS + HNAP_LOGIN_METHOD + '"' },
                    requestBody(HNAP_LOGIN_METHOD, loginRequest()));
            save_login_result(response);
            return soapAction(HNAP_LOGIN_METHOD, "LoginResult", requestBody(HNAP_LOGIN_METHOD, loginParameters()));
        } catch (Exception e) {
            logger.debug("dlinkplug Login: '{}'", e.getMessage());
            return "";
        }
    };

    private void save_login_result(String body) {
        try {
            Document doc = DOMParser.parse(new ByteArrayInputStream(body.getBytes("ISO-8859-1")));
            HNAP_AUTH.Result = doc.getElementsByTagName(HNAP_LOGIN_METHOD + "Result").item(0).getFirstChild()
                    .getNodeValue();
            HNAP_AUTH.Challenge = doc.getElementsByTagName("Challenge").item(0).getFirstChild().getNodeValue();
            HNAP_AUTH.PublicKey = doc.getElementsByTagName("PublicKey").item(0).getFirstChild().getNodeValue();
            HNAP_AUTH.Cookie = doc.getElementsByTagName("Cookie").item(0).getFirstChild().getNodeValue();
            HNAP_AUTH.PrivateKey = Hmac_md5.hex_hmac_md5(HNAP_AUTH.PublicKey + HNAP_AUTH.Pwd, HNAP_AUTH.Challenge)
                    .toUpperCase();
        } catch (Exception e) {
            logger.debug("dlinkplug save_login_result: '{}'", e.getMessage());
        }
    }

    private String requestBody(String method, String parameters) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" + "<soap:Envelope "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">" + "<soap:Body>" + "<" + method
                + " xmlns=\"" + HNAP1_XMLNS + "\">" + parameters + "</" + method + ">" + "</soap:Body></soap:Envelope>";
    }

    private String moduleParameters(String module) {
        return "<ModuleID>" + module + "</ModuleID>";
    }

    private String controlParameters(String module, String status) {
        return moduleParameters(module) + "<NickName>Socket 1</NickName><Description>Socket 1</Description>"
                + "<OPStatus>" + status + "</OPStatus><Controller>1</Controller>";
    }

    private String radioParameters(String radio) {
        return "<RadioID>" + radio + "</RadioID>";
    }

    private String soapAction(String method, String responseElement, String body) {
        try {
            String response = sendPost(HNAP_METHOD, HNAP_AUTH.URL,
                    new String[] { "Content-Type", "SOAPAction", "HNAP_AUTH", "Cookie" },
                    new String[] { "text/xml; charset=utf-8", '"' + HNAP1_XMLNS + method + '"',
                            getHnapAuth('"' + HNAP1_XMLNS + method + '"', HNAP_AUTH.PrivateKey),
                            "uid=" + HNAP_AUTH.Cookie },
                    body);
            return readResponseValue(response, responseElement);
        } catch (Exception e) {
            logger.debug("dlinkplug soapAction: '{}'", e.getMessage());
            return "";
        }
    }

    public String Plug_On() {
        return soapAction("SetSocketSettings", "SetSocketSettingsResult",
                requestBody("SetSocketSettings", controlParameters("1", "true")));
    };

    public String Plug_Off() {
        return soapAction("SetSocketSettings", "SetSocketSettingsResult",
                requestBody("SetSocketSettings", controlParameters("1", "false")));
    };

    public String Plug_State() {
        return soapAction("GetSocketSettings", "OPStatus", requestBody("GetSocketSettings", moduleParameters("1")));
    };

    public String Plug_Consumption() {
        return soapAction("GetCurrentPowerConsumption", "CurrentConsumption",
                requestBody("GetCurrentPowerConsumption", moduleParameters("2")));
    };

    public String Plug_TotalConsumption() {
        return soapAction("GetPMWarningThreshold", "TotalConsumption",
                requestBody("GetPMWarningThreshold", moduleParameters("2")));
    };

    public String Plug_Temperature() {
        return soapAction("GetCurrentTemperature", "CurrentTemperature",
                requestBody("GetCurrentTemperature", moduleParameters("3")));
    };

    public String Plug_GetAPClientSettings() {
        return soapAction("GetAPClientSettings", "GetAPClientSettingsResult",
                requestBody("GetAPClientSettings", radioParameters("RADIO_2.4GHz")));
    };

    public String Plug_SetPowerWarning() {
        return soapAction("SetPMWarningThreshold", "SetPMWarningThresholdResult",
                requestBody("SetPMWarningThreshold", powerWarningParameters()));
    };

    public String Plug_GetPowerWarning() {
        return soapAction("GetPMWarningThreshold", "GetPMWarningThresholdResult",
                requestBody("GetPMWarningThreshold", moduleParameters("2")));
    };

    public String Plug_GetTemperatureSettings() {
        return soapAction("GetTempMonitorSettings", "GetTempMonitorSettingsResult",
                requestBody("GetTempMonitorSettings", moduleParameters("3")));
    };

    public String Plug_SetTemperatureSettings() {
        return soapAction("SetTempMonitorSettings", "SetTempMonitorSettingsResult",
                requestBody("SetTempMonitorSettings", temperatureSettingsParameters("3")));
    };

    public String Plug_GetSiteSurvey() {
        return soapAction("GetSiteSurvey", "GetSiteSurveyResult",
                requestBody("GetSiteSurvey", radioParameters("RADIO_2.4GHz")));
    };

    public String Plug_TriggerWirelessSiteSurvey() {
        return soapAction("SetTriggerWirelessSiteSurvey", "SetTriggerWirelessSiteSurveyResult",
                requestBody("SetTriggerWirelessSiteSurvey", radioParameters("RADIO_2.4GHz")));
    };

    public String Plug_LatestDetection() {
        return soapAction("GetLatestDetection", "GetLatestDetectionResult",
                requestBody("GetLatestDetection", moduleParameters("2")));
    };

    public String Plug_Reboot() {
        return soapAction("Reboot", "RebootResult", requestBody("Reboot", ""));
    };

    public String Plug_IsDeviceReady() {
        return soapAction("IsDeviceReady", "IsDeviceReadyResult", requestBody("IsDeviceReady", ""));
    };

    public String Plug_GetModuleSchedule() {
        return soapAction("GetModuleSchedule", "GetModuleScheduleResult",
                requestBody("GetModuleSchedule", moduleParameters("0")));
    };

    public String Plug_GetModuleEnabled() {
        return soapAction("GetModuleEnabled", "GetModuleEnabledResult",
                requestBody("GetModuleEnabled", moduleParameters("0")));
    };

    public String Plug_GetModuleGroup() {
        return soapAction("GetModuleGroup", "GetModuleGroupResult",
                requestBody("GetModuleGroup", groupParameters("0")));
    };

    public String Plug_GetScheduleSettings() {
        return soapAction("GetScheduleSettings", "GetScheduleSettingsResult", requestBody("GetScheduleSettings", ""));
    };

    public String Plug_SetFactoryDefault() {
        return soapAction("SetFactoryDefault", "SetFactoryDefaultResult", requestBody("SetFactoryDefault", ""));
    };

    public String Plug_GetWLanRadios() {
        return soapAction("GetWLanRadios", "GetWLanRadiosResult", requestBody("GetWLanRadios", ""));
    };

    public String Plug_GetInternetSettings() {
        return soapAction("GetInternetSettings", "GetInternetSettingsResult", requestBody("GetInternetSettings", ""));
    };

    public String Plug_SetAPClientSettings() {
        return soapAction("SetAPClientSettings", "SetAPClientSettingsResult",
                requestBody("SetAPClientSettings", APClientParameters()));
    };

    public String Plug_SettriggerADIC() {
        return soapAction("SettriggerADIC", "SettriggerADICResult", requestBody("SettriggerADIC", ""));
    };

    private String APClientParameters() {
        // new AES();
        return "<Enabled>true</Enabled>" + "<RadioID>RADIO_2.4GHz</RadioID>" + "<SSID>My_Network</SSID>"
                + "<MacAddress>XX:XX:XX:XX:XX:XX</MacAddress>" + "<ChannelWidth>0</ChannelWidth>"
                + "<SupportedSecurity>" + "<SecurityInfo>" + "<SecurityType>WPA2-PSK</SecurityType>" + "<Encryptions>"
                + "<string>AES</string>" + "</Encryptions>" + "</SecurityInfo>" + "</SupportedSecurity>" + "<Key>"
                + org.openhab.binding.dlinksmartplugs.helpers.AES.encrypt("password", HNAP_AUTH.PrivateKey) + "</Key>";
    }

    private String groupParameters(String group) {
        return "<ModuleGroupID>" + group + "</ModuleGroupID>";
    }

    private String temperatureSettingsParameters(String module) {
        return moduleParameters(module) + "<NickName>TemperatureMonitor 3</NickName>"
                + "<Description>Temperature Monitor 3</Description>" + "<UpperBound>80</UpperBound>"
                + "<LowerBound>Not Available</LowerBound>" + "<OPStatus>true</OPStatus>";
    }

    private String powerWarningParameters() {
        return "<Threshold>28</Threshold>" + "<Percentage>70</Percentage>" + "<PeriodicType>Weekly</PeriodicType>"
                + "<StartTime>1</StartTime>";
    }

    private String loginRequest() {
        return "<Action>request</Action>" + "<Username>" + HNAP_AUTH.User + "</Username>"
                + "<LoginPassword></LoginPassword>" + "<Captcha></Captcha>";
    }

    private String loginParameters() {
        String login_pwd = Hmac_md5.hex_hmac_md5(HNAP_AUTH.PrivateKey, HNAP_AUTH.Challenge);
        return "<Action>login</Action>" + "<Username>" + HNAP_AUTH.User + "</Username>" + "<LoginPassword>"
                + login_pwd.toUpperCase() + "</LoginPassword>" + "<Captcha></Captcha>";
    }

    private String getHnapAuth(String SoapAction, String privateKey) {
        Date current_time = new Date();
        int time_stamp = Math.round(current_time.getTime() / 1000);
        String auth = Hmac_md5.hex_hmac_md5(privateKey, time_stamp + SoapAction);
        return auth.toUpperCase() + " " + time_stamp;
    }

    private String readResponseValue(String body, String elementName) {
        if (body != null && elementName != null) {
            try {
                Document doc = DOMParser.parse(new ByteArrayInputStream(body.getBytes("ISO-8859-1")));
                Node node = doc.getElementsByTagName(elementName).item(0);
                // Check that we have children of node.
                return (node != null && node.getFirstChild() != null) ? node.getFirstChild().getNodeValue() : "ERROR";
            } catch (Exception e) {
                logger.debug("dlinkplug readResponseValue: '{}'", e.getMessage());
            }
        }
        return "";
    }

    // HTTP POST request
    private String sendPost(String sMETHOD, String sURL, String[] headers, String[] headerValues, String sBODY) {
        try {
            URL obj = new URL(sURL);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();

            // add request header
            con.setRequestMethod(sMETHOD);
            if (headers != null) {
                for (int i = 0; i < headers.length; i++) {
                    con.setRequestProperty(headers[i], headerValues[i]);
                }
            }

            // Send post request
            con.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.writeBytes(sBODY);
            wr.flush();
            wr.close();

            // int responseCode = con.getResponseCode();

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // return result
            return response.toString();
        } catch (Exception e) {
            logger.debug("dlinkplug sendPost: '{}'", e.getMessage());
            return "";
        }

    }
}
