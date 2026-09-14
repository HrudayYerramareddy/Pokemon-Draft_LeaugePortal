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
    @Enumerated(EnumType.STRING)
    private TradeStatus status=TradeStatus.PENDING;
    private LocalDateTime createdAt=LocalDateTime.now();
    public TradeOffer(){}
    public TradeOffer(Long p,Long r,Long offered,Long requested){proposerTeamId=p;recipientTeamId=r;offeredPokemonId=offered;requestedPokemonId=requested;}
    public Long getId(){return id;} public Long getProposerTeamId(){return proposerTeamId;} public Long getRecipientTeamId(){return recipientTeamId;}
    public Long getOfferedPokemonId(){return offeredPokemonId;} public Long getRequestedPokemonId(){return requestedPokemonId;}
    public TradeStatus getStatus(){return status;} public void setStatus(TradeStatus v){status=v;} public LocalDateTime getCreatedAt(){return createdAt;}
}
