
package ca.uqam;

import ca.simple.JSONArray;
import ca.simple.JSONObject;
import ca.simple.parser.JSONParser;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;

import java.text.DateFormat;
import java.text.Format;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import static java.util.Calendar.DATE;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.Scanner;
import java.util.concurrent.Executors;

 // using SendGrid's Java Library
// https://github.com/sendgrid/sendgrid-java
import com.sendgrid.*;
import java.io.IOException;



//Auteurs : Latifa DAMOUH DAML11538206
//          IMANE MIFDAL  MIFI23558301
public class SmartHouse {	                 
                
	public static void main(String [] args) throws ParseException, IOException, ca.simple.parser.ParseException {
		ZMQ.Socket publisher;
		ZMQ.Socket subscriber;         
                JSONObject obj = new JSONObject();
                
                
		try (ZContext context = new ZContext()) {
                        
                        
                        HttpServer server = HttpServer.create(new InetSocketAddress(4444), 0);
                        /** On définit un endpoint "/test" ainsi qu'un gestionnaire qui va effectuer le traitement associé. */
                        server.createContext("/thermostat", new MyHandlerThermo());
                        server.createContext("/temperature", new MyHandlerTemp());
                        server.createContext("/door_lock", new MyHandlerDoor());
                        server.createContext("/presence", new MyHandlerPres());

                        /** 
                         * On utilise un executeur qui prend en charge un ensemble de threads fixe, pour le besoin 15 sera suffisant,
                         * si votre machine possède moins de ressource, vous pouvez descendre jusqu'à 5. 
                         */
                        server.setExecutor(Executors.newFixedThreadPool(15));
                        /** On démarre le serveur. */
                        server.start();
                    
                    
                    
			subscriber = context.createSocket(SocketType.SUB);
                        publisher = context.createSocket(SocketType.PUB); 
                        
                        publisher.bind("tcp://*:6666");                         
                        subscriber.connect("tcp://localhost:5555");  
                                                                     
                        subscriber.subscribe("temperature");
                        subscriber.subscribe("activity");
                        subscriber.subscribe("time");
                        subscriber.subscribe("door_lock_sensor");
                                                    
                        String tabActuateur[][] = {{"heater", "ac", "door_lock", "lights" },  {" ", " ", " " , " "}};
                        File f = new File("status.json");
                        if(f.isFile())
                        { 
                            f.delete();
                            f.createNewFile();
                        }else{
                            f.createNewFile();
                        }
                                                                        
                        float donneeTemp =0;  
                        int donneeActivity =-1;
                        
                        //SimpleDateFormat format = new SimpleDateFormat("hh:mm:ss");
                       // Date donneeTime = format.parse("00:00:00");
                        String donneeDoorLock="";
                        String dataThermostat="";
                        String dataDoorLock="";
                        float donneeThermo =0;
                        
                        while (true) {
                          
                            dataThermostat= lireJSONThermostat_request();
                            obj.put("thermostat" , dataThermostat);
                            dataDoorLock= lireJSONDoor_request();
                            obj.put("door_lock" , dataDoorLock);
                            
                            //Lecture du fichier status
                            //lireJSON();                                                      
                            
                            String topic = subscriber.recvStr();
                            if (topic == null)
                                break;
                            String data = subscriber.recvStr();                                          
                            
                                             
                            if ((topic.equals("temperature") && (donneeTemp != Float.parseFloat(data))) || (donneeThermo != Float.parseFloat(dataThermostat))) {                                                        
                                    
                                    if (topic.equals("temperature")){
                                        
                                        if ((donneeTemp != Float.parseFloat(data)) && (Float.parseFloat(data)>30 ) ){
                                           envoieAlert();
                                        }
                                        
                                        donneeTemp = Float.parseFloat(data);
                                        obj.put("temperature" , data);                                                                                                                   
                                        System.out.println("Température:" + donneeTemp);                                        
                                    }
                                    
                                    donneeThermo = Float.parseFloat(dataThermostat);                               
                                                                       
                                    if (donneeTemp+2 < Float.parseFloat(dataThermostat)){    
                                        
                                        if (!tabActuateur[1][0].equals("on")){
                                                                                        
                                            System.out.println("Démarrage du chauffage...");
                                            publisher.send("heater", ZMQ.SNDMORE);
                                            publisher.send("on");    
                                            tabActuateur[1][0]="on";
                                            obj.put("heater" , "on");
                                        }
                                        
                                    }else {     
                                        
                                        if (!tabActuateur[1][0].equals("off")){                                            
                                            System.out.println("Fermeture du chauffage...");
                                            publisher.send("heater", ZMQ.SNDMORE);
                                            publisher.send("off"); 
                                            tabActuateur[1][0]="off";
                                            obj.put("heater" , "off");
                                        }
                                    }                                                                                

                                    if (donneeTemp-2 > Float.parseFloat(dataThermostat)){    
                                       
                                        if (!tabActuateur[1][1].equals("on")){
                                            System.out.println("Démarrage de la climatisation...");
                                            publisher.send("ac", ZMQ.SNDMORE);
                                            publisher.send("on");    
                                            tabActuateur[1][1]= "on";
                                            obj.put("ac" , "on");
                                        }

                                    }else{       
                                        if (!tabActuateur[1][1].equals("off")){
                                            System.out.println("Fermeture de la climatisation...");
                                            publisher.send("ac", ZMQ.SNDMORE);
                                            publisher.send("off");     
                                            tabActuateur[1][1]= "off";
                                            obj.put("ac" , "off");
                                        }
                                    }              
                               
                            //}else if (topic.equals("time") && (!(donneeTime.equals(format.parse(data))))){
                            }else if (!dataDoorLock.equals(donneeDoorLock)  ){            
                                        //donneeTime = format.parse(data);                                      
                                       donneeDoorLock= dataDoorLock;
                                        //Date fixTime1 = format.parse("23:00:00");
                                        //Date fixTime2 = format.parse("07:00:00");
                                        
                                        //System.out.println("Heure:" + data );
                                        //obj.put("time", data);
                                                
                                        if (dataDoorLock.equals("on"))
                                        {                                             
                                            if (!tabActuateur[1][2].equals("on")){
                                                System.out.println("Vérouillage des portes");
                                                publisher.send("door_lock", ZMQ.SNDMORE);
                                                publisher.send("on"); 
                                                tabActuateur[1][2]="on";
                                                obj.put("door_lock", "on");
                                            }
                                        }else //if (donneeTime.equals(fixTime2)) 
                                        {        
                                            if (!tabActuateur[1][2].equals("off")){
                                                System.out.println("Déverouillage des portes");
                                                publisher.send("door_lock", ZMQ.SNDMORE);
                                                publisher.send("off"); 
                                                tabActuateur[1][2]="off";
                                                obj.put("door_lock", "off");
                                            }
                                        }    
                               
                            }else if (topic.equals("activity")&&(donneeActivity != Integer.parseInt(data))){                               
                               
                                        donneeActivity = Integer.parseInt(data);
                                        System.out.println("Présence:" + data );
                                        obj.put("activity", data);

                                        if (donneeActivity ==1){   

                                             if (!tabActuateur[1][3].equals("on")){

                                                 System.out.println("Ouverture des lumières");
                                                 publisher.send("lights", ZMQ.SNDMORE);
                                                 publisher.send("on");    
                                                 tabActuateur[1][3]="on";
                                                 obj.put("lights", "on");
                                             }

                                        }else if (donneeActivity ==0){ 

                                             if (!tabActuateur[1][3].equals("off")){

                                                 System.out.println("Férmeture des lumières");
                                                 publisher.send("lights", ZMQ.SNDMORE);
                                                 publisher.send("off");    
                                                 tabActuateur[1][3]="off";
                                                 obj.put("lights", "off");
                                            }
                                        }                                                      
                            
                        }                                      
                        ecrireJSONStatus(obj);
                }                                                                
    }        
    }   
        
        
        static void ecrireJSONStatus(JSONObject obj ){
            
            try (FileWriter file = new FileWriter("status.JSON")) {

                file.write(obj.toString());
                file.flush();

            } catch (IOException e) {
                e.printStackTrace();
            }
                       
        }
        
        static String lireJSONThermostat_request() throws ca.simple.parser.ParseException, IOException{
            JSONParser parser = new JSONParser();
            String thermostat="";
            try {
                Object obj = parser.parse(new FileReader("thermostat_request.JSON"));
                JSONObject jsonObject = (JSONObject) obj;
                thermostat = (String) jsonObject.get("valeur");
                //System.out.println(thermostat);           

            } catch (FileNotFoundException e) {
                e.printStackTrace();       
            }
            return thermostat;
        }
        
        static String lireJSONDoor_request() throws ca.simple.parser.ParseException, IOException{
            JSONParser parser = new JSONParser();
            String door_lock= "";
            try {
                Object obj = parser.parse(new FileReader("door_request.json"));
                JSONObject jsonObject = (JSONObject) obj;
                door_lock = (String) jsonObject.get("valeur");
                //System.out.println(door_lock);           

            } catch (FileNotFoundException e) {
                e.printStackTrace();       
            }
            
            return door_lock;
        }

        
        
        
        static class MyHandlerThermo implements HttpHandler {

        @Override
        public void handle(HttpExchange he) throws IOException {
            /** Récupère le type de méthode de la requête : GET, PUT, ... */
            String requestMethod = he.getRequestMethod();
            String response="";
            
            /** Traitement dépendant de la méthode de la requête, agit comme un controleur. */
            if (requestMethod.equalsIgnoreCase("GET")) {
                                
                try {
                    response = lireJSONStatus("thermostat");
                   
                    
                } catch (ca.simple.parser.ParseException ex) {
                    Logger.getLogger(SmartHouse.class.getName()).log(Level.SEVERE, null, ex);
                }
                /** Permet d'ajouter un type de contenu à la requête, information utile pour le navigateur. */
                addResponseHeader(he);
                /** Envoie le code de réponse ainsi que la taille du message de réponse. */
                he.sendResponseHeaders(200, response.length());
                /** Récupère le flux de sortie du corps de la reponse. */
                try (OutputStream os = he.getResponseBody()) {
                  /** On écrit dans le flux de sortie le message de réponse. */
                    os.write(response.getBytes());
                } /** On ferme le flux de sortie, ce qui termine l'envoie de la réponse. */   
            }else if (requestMethod.equalsIgnoreCase("PUT")) {
                try {
                        
                    String nouvValeur = he.getRequestURI().toString().substring(he.getRequestURI().toString().indexOf("?=") + 2, he.getRequestURI().toString().length());
                  
                        createJsonPutFile ("thermostat_request.JSON" , nouvValeur )  ;

                    } catch (Exception ex) {
                        Logger.getLogger(SmartHouse.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    addResponseHeader(he);
                    he.sendResponseHeaders(200, response.length());
                    try (OutputStream os = he.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                }                                  
            
        }
        
        }
        
        
        static class MyHandlerTemp implements HttpHandler {

        @Override
        public void handle(HttpExchange he) throws IOException {
            /** Récupère le type de méthode de la requête : GET, PUT, ... */
            String requestMethod = he.getRequestMethod();
            String response="";
            
            /** Traitement dépendant de la méthode de la requête, agit comme un controleur. */
            if (requestMethod.equalsIgnoreCase("GET")) {
                                
                try {
                    response = lireJSONStatus("temperature");
                   
                    
                } catch (ca.simple.parser.ParseException ex) {
                    Logger.getLogger(SmartHouse.class.getName()).log(Level.SEVERE, null, ex);
                }
                /** Permet d'ajouter un type de contenu à la requête, information utile pour le navigateur. */
                addResponseHeader(he);
                /** Envoie le code de réponse ainsi que la taille du message de réponse. */
                he.sendResponseHeaders(200, response.length());
                /** Récupère le flux de sortie du corps de la reponse. */
                try (OutputStream os = he.getResponseBody()) {
                  /** On écrit dans le flux de sortie le message de réponse. */
                    os.write(response.getBytes());
                } /** On ferme le flux de sortie, ce qui termine l'envoie de la réponse. */                                           
            }
        }
        
        }
        
        
        static class MyHandlerDoor implements HttpHandler {

        @Override
        public void handle(HttpExchange he) throws IOException {
            /** Récupère le type de méthode de la requête : GET, PUT, ... */
            String requestMethod = he.getRequestMethod();
            String response="";
            
            /** Traitement dépendant de la méthode de la requête, agit comme un controleur. */
            if (requestMethod.equalsIgnoreCase("GET")) {
                                
                try {
                    response = lireJSONStatus("door_lock");
                   
                    
                } catch (ca.simple.parser.ParseException ex) {
                    Logger.getLogger(SmartHouse.class.getName()).log(Level.SEVERE, null, ex);
                }
                /** Permet d'ajouter un type de contenu à la requête, information utile pour le navigateur. */
                addResponseHeader(he);
                /** Envoie le code de réponse ainsi que la taille du message de réponse. */
                he.sendResponseHeaders(200, response.length());
                /** Récupère le flux de sortie du corps de la reponse. */
                try (OutputStream os = he.getResponseBody()) {
                  /** On écrit dans le flux de sortie le message de réponse. */
                    os.write(response.getBytes());
                } /** On ferme le flux de sortie, ce qui termine l'envoie de la réponse. */                
                           
            }else if (requestMethod.equalsIgnoreCase("PUT")) {
                    try {
                        
                    String nouvValeur = he.getRequestURI().toString().substring(he.getRequestURI().toString().indexOf("?=") + 2, he.getRequestURI().toString().length());
                  //  if (this.getMessage().equalsIgnoreCase("thermostat") )  {
                        //ecrireJSONDoor (nouvValeur ) ;                         
                        createJsonPutFile ("door_request.JSON" , nouvValeur )  ;


                    } catch (Exception ex) {
                        Logger.getLogger(SmartHouse.class.getName()).log(Level.SEVERE, null, ex);
                    }
                
                    addResponseHeader(he);
                    he.sendResponseHeaders(200, response.length());
                    try (OutputStream os = he.getResponseBody()) {
                        os.write(response.getBytes());
                    }
                    
            }     
                                    
            }
        }
        
        
        
        
        static class MyHandlerPres implements HttpHandler {

        @Override
        public void handle(HttpExchange he) throws IOException {
            /** Récupère le type de méthode de la requête : GET, PUT, ... */
            String requestMethod = he.getRequestMethod();
            String response="";
            
            /** Traitement dépendant de la méthode de la requête, agit comme un controleur. */
            if (requestMethod.equalsIgnoreCase("GET")) {
                                
                try {
                    response = lireJSONStatus("activity");
                   
                    
                } catch (ca.simple.parser.ParseException ex) {
                    Logger.getLogger(SmartHouse.class.getName()).log(Level.SEVERE, null, ex);
                }
                /** Permet d'ajouter un type de contenu à la requête, information utile pour le navigateur. */
                addResponseHeader(he);
                /** Envoie le code de réponse ainsi que la taille du message de réponse. */
                he.sendResponseHeaders(200, response.length());
                /** Récupère le flux de sortie du corps de la reponse. */
                try (OutputStream os = he.getResponseBody()) {
                  /** On écrit dans le flux de sortie le message de réponse. */
                    os.write(response.getBytes());
                } /** On ferme le flux de sortie, ce qui termine l'envoie de la réponse. */                
                           
            }
        }
        
        }
        
        public static void  createJsonPutFile( String fileName , String jsonObj){
        
        JSONObject  obj = null ; 
        try (FileWriter file = new FileWriter( fileName)) {
            if (fileName.equalsIgnoreCase("thermostat_request.JSON") ) 
                obj = creerJsonObj(jsonObj);
            if (fileName.equalsIgnoreCase("door_request.JSON") )   
                obj = creerJsonObj(jsonObj);
                file.write(obj.toJSONString());
                file.flush();
            } catch (IOException e) {
                e.printStackTrace();
        }
        }

        
         public static JSONObject creerJsonObj(String Obj) {  
 
            JSONObject  objJSON = new JSONObject();         
            objJSON.put("valeur", Obj);// Float.parseFloat(Obj));
            return objJSON ; 

        } 

         
        private static void addResponseHeader(HttpExchange httpExchange) {
            List<String> contentTypeValue = new ArrayList<>();
            contentTypeValue.add("application/json");
            httpExchange.getResponseHeaders().put("Content-Type", contentTypeValue);
        }
        
              
        static String lireJSONStatus(String attribut) throws ca.simple.parser.ParseException, IOException{
            JSONParser parser = new JSONParser();
            String valeur = "";
            try {               

                 Object obj = parser.parse(new FileReader("status.json"));                 
                 JSONObject jsonObject = (JSONObject) obj;
                 valeur =(String) jsonObject.get(attribut);                                     

            } catch (FileNotFoundException e) {
                e.printStackTrace();       
            }
            return valeur;
        }
        
        static void envoieAlert() throws IOException, ca.simple.parser.ParseException{

            Email from = new Email("imanos500@hotmail.com");
            String subject = "Alert de Température";
            Email to = new Email("imanos500@hotmail.com");
            Content content = new Content("Alert", "La température est au dessus de 30degrés");
            Mail mail = new Mail(from, subject, to, content);

           SendGrid sg = new SendGrid("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");
            Request request = new Request();
            
            try{
                
              request.setMethod(Method.POST);
              request.setEndpoint("mail/send");
              request.setBody(mail.build());
              //Response response =               
              sg.api(request);
              //System.out.println(response.getStatusCode());
              //System.out.println(response.getBody());
              //System.out.println(response.getHeaders());
              
              Request request1 = new Request();
              request1.setMethod(Method.GET);                            
              request1.setEndpoint("stats");  
              request1.addQueryParam("start_date", "2018-11-01");
              request1.addQueryParam("aggregated_by", "month");
                    
              Response response1 = sg.api(request1);
              //System.out.println(response1.getBody());
      
              String str= response1.getBody();
              
              int nbEnv = str.indexOf("requests") + 10 ; 
              int nbNonEnv = str.indexOf("spam_report_drops") -2 ;        
              System.out.println("Nombre d'emails envoyés : " + str.substring(nbEnv, nbNonEnv )) ;                    
                                                 
            }catch (IOException ex) {
                throw ex;
            }
            
        }
     
}
        
        
    

        
