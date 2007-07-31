/*******************************************************************************
 * Copyright (c) quickfixengine.org  All rights reserved. 
 * 
 * This file is part of the QuickFIX FIX Engine 
 * 
 * This file may be distributed under the terms of the quickfixengine.org 
 * license as defined by quickfixengine.org and appearing in the file 
 * LICENSE included in the packaging of this file. 
 * 
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING 
 * THE WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A 
 * PARTICULAR PURPOSE. 
 * 
 * See http://www.quickfixengine.org/LICENSE for licensing information. 
 * 
 * Contact ask@quickfixengine.org if any conditions of this licensing 
 * are not clear to you.
 ******************************************************************************/

package quickfix.examples.executor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import quickfix.ConfigError;
import quickfix.DoNotSend;
import quickfix.FieldConvertError;
import quickfix.FieldNotFound;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.Message;
import quickfix.RejectLogon;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionNotFound;
import quickfix.SessionSettings;
import quickfix.UnsupportedMessageType;
import quickfix.field.AvgPx;
import quickfix.field.CumQty;
import quickfix.field.ExecID;
import quickfix.field.ExecTransType;
import quickfix.field.ExecType;
import quickfix.field.LastPx;
import quickfix.field.LastQty;
import quickfix.field.LastShares;
import quickfix.field.LeavesQty;
import quickfix.field.OrdStatus;
import quickfix.field.OrdType;
import quickfix.field.OrderID;
import quickfix.field.OrderQty;
import quickfix.field.Price;
import quickfix.field.Symbol;

public class Application extends quickfix.MessageCracker implements quickfix.Application {
    private static final String DEFAULT_MARKET_PRICE_KEY = "DefaultMarketPrice";
    private static final String VALID_ORDER_TYPES_KEY = "ValidOrderTypes";
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final HashSet<String> validOrderTypes = new HashSet<String>();
    private MarketDataProvider marketDataProvider;

    public Application(SessionSettings settings) throws ConfigError, FieldConvertError {
        initializeValidOrderTypes(settings);
        initializeMarketDataProvider(settings);
    }

    private void initializeMarketDataProvider(SessionSettings settings) throws ConfigError,
            FieldConvertError {
        if (settings.isSetting(DEFAULT_MARKET_PRICE_KEY)) {
            if (marketDataProvider == null) {
                final double defaultMarketPrice = settings.getDouble(DEFAULT_MARKET_PRICE_KEY);
                marketDataProvider = new MarketDataProvider() {
                    @Override
                    public double getPrice(String symbol) {
                        return defaultMarketPrice;
                    }

                };
            } else {
                log.warn("Ignoring " + DEFAULT_MARKET_PRICE_KEY
                        + " since provider is already defined.");
            }
        }
    }

    private void initializeValidOrderTypes(SessionSettings settings) throws ConfigError,
            FieldConvertError {
        if (settings.isSetting(VALID_ORDER_TYPES_KEY)) {
            List<String> orderTypes = Arrays.asList(settings.getString(VALID_ORDER_TYPES_KEY)
                    .trim().split("\\s*,\\s*"));
            validOrderTypes.addAll(orderTypes);
        } else {
            validOrderTypes.add(OrdType.LIMIT + "");
        }
    }

    public void onCreate(SessionID sessionID) {
        Session.lookupSession(sessionID).getLog().onEvent("Valid order types: " + validOrderTypes);
    }

    public void onLogon(SessionID sessionID) {
    }

    public void onLogout(SessionID sessionID) {
    }

    public void toAdmin(quickfix.Message message, SessionID sessionID) {
    }

    public void toApp(quickfix.Message message, SessionID sessionID) throws DoNotSend {
    }

    public void fromAdmin(quickfix.Message message, SessionID sessionID) throws FieldNotFound,
            IncorrectDataFormat, IncorrectTagValue, RejectLogon {
    }

    public void fromApp(quickfix.Message message, SessionID sessionID) throws FieldNotFound,
            IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        crack(message, sessionID);
    }

    public void onMessage(quickfix.fix40.NewOrderSingle order, SessionID sessionID)
            throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
        validateOrder(order);

        OrderQty orderQty = order.getOrderQty();

        Price price = getPrice(order);

        quickfix.fix40.ExecutionReport accept = new quickfix.fix40.ExecutionReport(genOrderID(),
                genExecID(), new ExecTransType(ExecTransType.NEW), new OrdStatus(OrdStatus.NEW),
                order.getSymbol(), order.getSide(), orderQty, new LastShares(0), new LastPx(0),
                new CumQty(0), new AvgPx(0));

        accept.set(order.getClOrdID());
        sendMessage(sessionID, accept);

        quickfix.fix40.ExecutionReport fill = new quickfix.fix40.ExecutionReport(genOrderID(),
                genExecID(), new ExecTransType(ExecTransType.NEW), new OrdStatus(OrdStatus.FILLED),
                order.getSymbol(), order.getSide(), orderQty, new LastShares(orderQty.getValue()),
                new LastPx(price.getValue()), new CumQty(orderQty.getValue()), new AvgPx(price
                        .getValue()));

        fill.set(order.getClOrdID());

        sendMessage(sessionID, fill);
    }

    private Price getPrice(Message message) throws FieldNotFound {
        Price price;
        if (message.isSetField(Price.FIELD)) {
            price = new Price(message.getDouble(Price.FIELD));
        } else {
            if (marketDataProvider == null) {
                throw new RuntimeException("No market data provider specified for market order");
            }
            price = new Price(marketDataProvider.getPrice(message.getString(Symbol.FIELD)));
        }
        return price;
    }

    private void sendMessage(SessionID sessionID, Message message) {
        try {
            Session.sendToTarget(message, sessionID);
        } catch (SessionNotFound e) {
            log.error(e.getMessage(), e);
        }
    }

    public void onMessage(quickfix.fix41.NewOrderSingle order, SessionID sessionID)
            throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
        validateOrder(order);

        OrderQty orderQty = order.getOrderQty();
        Price price = getPrice(order);

        quickfix.fix41.ExecutionReport executionReport = new quickfix.fix41.ExecutionReport(
                genOrderID(), genExecID(), new ExecTransType(ExecTransType.NEW), new ExecType(
                        ExecType.FILL), new OrdStatus(OrdStatus.FILLED), order.getSymbol(), order
                        .getSide(), orderQty, new LastShares(orderQty.getValue()), new LastPx(price
                        .getValue()), new LeavesQty(0), new CumQty(orderQty.getValue()), new AvgPx(
                        price.getValue()));

        executionReport.set(order.getClOrdID());

        sendMessage(sessionID, executionReport);
    }

    public void onMessage(quickfix.fix42.NewOrderSingle order, SessionID sessionID)
            throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
        validateOrder(order);

        OrderQty orderQty = order.getOrderQty();
        Price price = getPrice(order);

        quickfix.fix42.ExecutionReport executionReport = new quickfix.fix42.ExecutionReport(
                genOrderID(), genExecID(), new ExecTransType(ExecTransType.NEW), new ExecType(
                        ExecType.FILL), new OrdStatus(OrdStatus.FILLED), order.getSymbol(), order
                        .getSide(), new LeavesQty(0), new CumQty(orderQty.getValue()), new AvgPx(
                        price.getValue()));

        executionReport.set(order.getClOrdID());
        executionReport.set(orderQty);
        executionReport.set(new LastShares(orderQty.getValue()));
        executionReport.set(new LastPx(price.getValue()));

        sendMessage(sessionID, executionReport);
    }

    private void validateOrder(Message order) throws IncorrectTagValue, FieldNotFound {
        OrdType ordType = new OrdType(order.getChar(OrdType.FIELD));
        if (!validOrderTypes.contains(Character.toString(ordType.getValue()))) {
            log.error("Order type not in ValidOrderTypes setting");
            throw new IncorrectTagValue(ordType.getField());
        }
        if (ordType.getValue() == OrdType.MARKET && marketDataProvider == null) {
            log.error("DefaultMarketPrice setting not specified for market order");
            throw new IncorrectTagValue(ordType.getField());
        }
    }

    public void onMessage(quickfix.fix43.NewOrderSingle order, SessionID sessionID)
            throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
        validateOrder(order);

        OrderQty orderQty = order.getOrderQty();
        Price price = getPrice(order);

        quickfix.fix43.ExecutionReport executionReport = new quickfix.fix43.ExecutionReport(
                genOrderID(), genExecID(), new ExecType(ExecType.FILL), new OrdStatus(
                        OrdStatus.FILLED), order.getSide(), new LeavesQty(0), new CumQty(orderQty
                        .getValue()), new AvgPx(price.getValue()));

        executionReport.set(order.getClOrdID());
        executionReport.set(order.getSymbol());
        executionReport.set(orderQty);
        executionReport.set(new LastQty(orderQty.getValue()));
        executionReport.set(new LastPx(price.getValue()));

        sendMessage(sessionID, executionReport);
    }

    public void onMessage(quickfix.fix44.NewOrderSingle order, SessionID sessionID)
            throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
        validateOrder(order);

        OrderQty orderQty = order.getOrderQty();
        Price price = getPrice(order);

        quickfix.fix44.ExecutionReport executionReport = new quickfix.fix44.ExecutionReport(
                genOrderID(), genExecID(), new ExecType(ExecType.FILL), new OrdStatus(
                        OrdStatus.FILLED), order.getSide(), new LeavesQty(0), new CumQty(orderQty
                        .getValue()), new AvgPx(price.getValue()));

        executionReport.set(order.getClOrdID());
        executionReport.set(order.getSymbol());
        executionReport.set(orderQty);
        executionReport.set(new LastQty(orderQty.getValue()));
        executionReport.set(new LastPx(price.getValue()));

        sendMessage(sessionID, executionReport);
    }

    public OrderID genOrderID() {
        return new OrderID(Integer.valueOf(++m_orderID).toString());
    }

    public ExecID genExecID() {
        return new ExecID(Integer.valueOf(++m_execID).toString());
    }

    /**
     * Allows a custom market data provider to be specified.
     * 
     * @param marketDataProvider
     */
    public void setMarketDataProvider(MarketDataProvider marketDataProvider) {
        this.marketDataProvider = marketDataProvider;
    }
    
    private int m_orderID = 0;
    private int m_execID = 0;
}