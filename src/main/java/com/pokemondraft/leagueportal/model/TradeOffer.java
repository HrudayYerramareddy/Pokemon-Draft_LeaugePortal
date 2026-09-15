package com.pokemondraft.leagueportal.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class TradeOffer {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    private Long proposerTeamId;
    private Long recipientTeamId;
    private Long offeredPokemonId;
    private Long requestedPokemonId;
    private int proposerFaab;
    private int recipientFaab;
    @Enumerated(EnumType.STRING)
    private TradeStatus status=TradeStatus.PENDING;
    private LocalDateTime createdAt=LocalDateTime.now();
    public TradeOffer(){}
    public TradeOffer(Long p,Long r,Long offered,Long requested){this(p,r,offered,requested,0,0);}
    public TradeOffer(Long p,Long r,Long offered,Long requested,int proposerFaab,int recipientFaab){this.proposerTeamId=p;this.recipientTeamId=r;this.offeredPokemonId=offered;this.requestedPokemonId=requested;this.proposerFaab=proposerFaab;this.recipientFaab=recipientFaab;}
    public Long getId(){return id;} public Long getProposerTeamId(){return proposerTeamId;} public Long getRecipientTeamId(){return recipientTeamId;}
    public Long getOfferedPokemonId(){return offeredPokemonId;} public Long getRequestedPokemonId(){return requestedPokemonId;}
    public int getProposerFaab(){return proposerFaab;} public int getRecipientFaab(){return recipientFaab;}
    public TradeStatus getStatus(){return status;} public void setStatus(TradeStatus v){status=v;} public LocalDateTime getCreatedAt(){return createdAt;}
}
