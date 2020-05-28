package it.polito.tdp.Emergency.model;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

import it.polito.tdp.Emergency.model.Event.EventType;
import it.polito.tdp.Emergency.model.Paziente.CodiceColore;

public class Simulator {

	//PARAMETRI DI SIMULAZIONE 	
	private int NS=5;//numero studi medici
	private int NP=150;//numero di pazienti
	private Duration T_ARRIVAL=Duration.ofMinutes(5);//intervallo tra i pazienti
	private final Duration DURATION_TRIAGE=Duration.ofMinutes(5);
	private final Duration DURATION_WHITE=Duration.ofMinutes(10);
	private final Duration DURATION_YELLOW=Duration.ofMinutes(15);
	private final Duration DURATION_RED=Duration.ofMinutes(30);
	
	private final Duration TIMEOUT_WHITE=Duration.ofMinutes(90);
	private final Duration TIMEOUT_YELLOW=Duration.ofMinutes(30);
	private final Duration TIMEOUT_RED=Duration.ofMinutes(60);
	
	private final Duration TIC_TIME=Duration.ofMinutes(5);
	
	private final LocalTime oraInizio=LocalTime.of(8,0);
	private final LocalTime oraFine=LocalTime.of(20, 0);
	
	//OUTPUT DA CALCOLARE
	private int pazientiTot;
	private int pazientiDimessi;
	private int pazientiAbbandonano;
	private int pazientiMorti;
	
	//STATO DEL SISTEMA
	private List<Paziente> pazienti;
	private PriorityQueue<Paziente> attesa;//solo i pazienti post-triage, prima di essere chiamati
	private CodiceColore ultimoColoreAssegnato=CodiceColore.WHITE;
	private int studiLiberi;
	
	//CODA DEGLI EVENTI 
	PriorityQueue<Event> coda;
	
	//INIZIALIZZAZIONE
	public void init() {
		this.coda=new PriorityQueue<>();
		this.pazienti=new ArrayList<>();
		this.attesa=new PriorityQueue<>();
		this.pazientiTot=0;
		this.pazientiDimessi=0;
		this.pazientiAbbandonano=0;
		this.pazientiMorti=0;
		this.ultimoColoreAssegnato=CodiceColore.WHITE;
		this.studiLiberi=this.NS;
		//generare gli eventi iniziali
		int nPaz=0;//numero di pazienti aggiunti
		LocalTime oraArrivo=this.oraInizio;//ora di arrivo del pazienti n-esimo
		
		while(nPaz<this.NP && oraArrivo.isBefore(this.oraFine)) {
			Paziente p=new Paziente(oraArrivo,CodiceColore.UNKNOWN);
			this.pazienti.add(p);
			Event e=new Event(oraArrivo, EventType.ARRIVAL,p);
			this.coda.add(e);
			nPaz++;
			oraArrivo=oraArrivo.plus(T_ARRIVAL);
		}
		
	}

	//ESECUZIONE
	public void run() {
		while(!this.coda.isEmpty()) {
			Event e=this.coda.poll();
			processEvent(e);
		}
	}
	
	private void processEvent(Event e) {
		 Paziente paz=e.getPaziente();
		switch(e.getType()) {
		case ARRIVAL:
			//arriva un paziente, tra 5 minuti sarà finito il triage
			this.coda.add(new Event(e.getTime().plus(DURATION_TRIAGE),EventType.TRIAGE,paz));
			this.pazientiTot++;
			break;
		case TRIAGE:
			//assegna codice colore
			paz.setColore(nuovoCodiceColore());
			//mette in lista di attesa
			this.attesa.add(paz);
			//schedula timeout
			if(paz.getColore()==CodiceColore.WHITE) {
				coda.add(new Event(e.getTime().plus(TIMEOUT_WHITE),EventType.TIMEOUT,paz));
			}else if(paz.getColore()==CodiceColore.YELLOW	) {
				coda.add(new Event(e.getTime().plus(TIMEOUT_YELLOW),EventType.TIMEOUT,paz));
			}else if(paz.getColore()==CodiceColore.RED) {
				coda.add(new Event(e.getTime().plus(TIMEOUT_RED),EventType.TIMEOUT,paz));
			}
			break;
		case FREE_STUDIO:
			if(this.studiLiberi==0) // non ci sono studi liberi
				break ;
			Paziente prossimo=attesa.poll();
			if(prossimo!=null) {
				//fallo entrare
				this.studiLiberi--;
				
				//schedula l'uscita dallo studio
				if(prossimo.getColore()==CodiceColore.WHITE) {
					coda.add(new Event(e.getTime().plus(DURATION_WHITE),EventType.TREATED,prossimo));
				}else if(prossimo.getColore()==CodiceColore.YELLOW	) {
					coda.add(new Event(e.getTime().plus(DURATION_YELLOW),EventType.TREATED,prossimo));
				}else if(prossimo.getColore()==CodiceColore.RED) {
					coda.add(new Event(e.getTime().plus(DURATION_RED),EventType.TREATED,prossimo));
				}
			}
			break;
		case TREATED:
			//libera lo studio
			this.studiLiberi++;
			paz.setColore(CodiceColore.OUT);
			
			this.pazientiDimessi++;
			this.coda.add(new Event(e.getTime(),EventType.FREE_STUDIO,null));
			break;
		case TIMEOUT:
			//esci dalla lista d'attesa
			boolean eraPresente=attesa.remove(paz);
			if(!eraPresente)
				break;
			
			switch(paz.getColore()) {
			case WHITE:
				//va a casa
				this.pazientiAbbandonano++;
				break;
			case YELLOW:
				//diventa RED
				paz.setColore(CodiceColore.RED);
				attesa.add(paz);
				coda.add(new Event(e.getTime().plus(DURATION_RED),EventType.TIMEOUT,paz));
				break;
			case RED:
				//muore
				this.pazientiMorti++;
				paz.setColore(CodiceColore.OUT);
				break;
			}
			break;
		case TIC:
			if(this.studiLiberi>0) {
				//schedula un ingresso di un paziente, adesso in questo momento
				this.coda.add(new Event(e.getTime(),EventType.FREE_STUDIO,null));
			}
			if(e.getTime().isBefore(LocalTime.of(23, 30)))
				this.coda.add(new Event(e.getTime().plus(this.TIC_TIME), EventType.TIC, null));
			//this.coda.add(new Event(e.getTime().plus(TIC_TIME),EventType.TIC,null));
			//ogni 5 minuti controllo se ci sono studi disponibili e inutilizzati
			break;
		}
	}
	
	
	private CodiceColore nuovoCodiceColore() {
		CodiceColore nuovo=this.ultimoColoreAssegnato;
		
		if(this.ultimoColoreAssegnato==CodiceColore.WHITE) {
			this.ultimoColoreAssegnato=CodiceColore.YELLOW;
		}else if(this.ultimoColoreAssegnato==CodiceColore.YELLOW) {
			this.ultimoColoreAssegnato=CodiceColore.RED;
		}else 
			this.ultimoColoreAssegnato=CodiceColore.WHITE;
		return nuovo;
	}

	//VALORI DI INPUT
	/**
	 * Set the number of medical studio
	 * @param NS
	 */
	public void setNS(int nS) {
		NS = nS;
	}
	/**
	 * Set the number of the total patient of the simulation
	 * @param nP
	 */
	public void setNP(int nP) {
		NP = nP;
	}
	/**
	 * Inter-arrival time beetween patient
	 * @param t_ARRIVAL
	 */
	public void setT_ARRIVAL(Duration t_ARRIVAL) {
		T_ARRIVAL = t_ARRIVAL;
	}
	
	//GETTER PER ACCEDERE ALLE VARIABILI DEL MONDO
	public int getNS() {
		// TODO Auto-generated method stub
		return this.NS;
	}

	public int getPazientiTot() {
		// TODO Auto-generated method stub
		return this.pazientiTot;
	}

	public int getPazientiDimessi() {
		// TODO Auto-generated method stub
		return this.pazientiDimessi;
	}

	public int getPazientiMorti() {
		// TODO Auto-generated method stub
		return this.pazientiMorti;
	}

	public int getPazientiAbbandonano() {
		// TODO Auto-generated method stub
		return this.pazientiAbbandonano;
	}
	
}
