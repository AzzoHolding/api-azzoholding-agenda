package br.com.phdigitalcode.azzo.agenda.pro.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Espelha {@code application/dto/contract/SalonDtos.java}: {@code BusinessHour}/
 * {@code SpecialClosureDate} sao consumidos pelo modulo {@code settings};
 * {@code SalonProfile}/{@code PublicSalonProfile} pelo modulo {@code salon}.
 *
 * <p>Campos publicos porque o original tambem os expoe assim e o contrato JSON serializado precisa
 * bater campo a campo com o que o frontend ja consome.
 */
public final class SalonDtos {

  private SalonDtos() {}

  public static class BusinessHour {
    public String day;
    public boolean enabled;
    public String open;
    public String close;
  }

  public static class SpecialClosureDate {
    public String date;
    public String reason;
  }

  public static class SalonProfile {
    public String salonName;
    public String salonSlug;
    public String publicBookingUrl;
    public String logo;
    public String logoUrl;
    public String salonDescription;
    public String salonPhone;
    public String salonWhatsapp;
    public String salonCpfCnpj;
    public String salonEmail;
    public String salonWebsite;
    public String salonInstagram;
    public String salonFacebook;
    public String street;
    public String number;
    public String complement;
    public String neighborhood;
    public String city;
    public String state;
    public String zipCode;
    public List<BusinessHour> businessHours = new ArrayList<>();
    public List<SpecialClosureDate> specialClosureDates = new ArrayList<>();
  }

  public static class PublicSalonProfile {
    public String salonName;
    public String salonSlug;
    public String publicBookingUrl;
    public String logo;
    public String logoUrl;
    public String salonDescription;
    public String salonPhone;
    public String salonWhatsapp;
    public List<BusinessHour> businessHours = new ArrayList<>();
    /** Indica se o agendamento publico exige sinal (deposito antecipado). Reservado para implementacao futura. */
    public boolean depositRequired = false;
    /** Valor do sinal em centavos. Nulo quando depositRequired for false. */
    public Long depositAmount = null;
  }
}
