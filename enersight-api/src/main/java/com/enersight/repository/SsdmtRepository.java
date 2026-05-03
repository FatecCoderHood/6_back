package com.enersight.repository;

import com.enersight.dto.SsdmtGeoDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class SsdmtRepository {

    @PersistenceContext
    private final EntityManager em;

    public List<Object[]> findGeoData(
            int year,
            double minx,
            double miny,
            double maxx,
            double maxy
    ) {
        String sql = """
            WITH indq_agg AS (
                SELECT
                    "Conjunto_ID",
                    MAX("Score_Criticidade") AS score_criticidade,
                    MAX("DEC_limite")        AS dec_limite,
                    MAX("FEC_limite")        AS fec_limite,
                    AVG("DEC_realizado")     AS dec_realizado,
                    AVG("FEC_realizado")     AS fec_realizado,
                    AVG("Desvio_DEC")        AS desvio_dec,
                    AVG("Desvio_FEC")        AS desvio_fec
                FROM staging.enel_sp_indq
                WHERE "Ano" = :year
                GROUP BY "Conjunto_ID"
            )
            SELECT
                s.conj,
                ST_AsGeoJSON(ST_Simplify(s.shape, 0.0001)),
                i.dec_limite,
                i.fec_limite,
                i.dec_realizado,
                i.fec_realizado,
                i.desvio_dec,
                i.desvio_fec,
                COALESCE(i.score_criticidade, 0)
            FROM staging.enel_sp_ssdmt s
            LEFT JOIN indq_agg i
                ON s.conj = i."Conjunto_ID"
            WHERE ST_Intersects(
                s.shape,
                ST_MakeEnvelope(:minx, :miny, :maxx, :maxy, 4674)
            )
        """;

        return em.createNativeQuery(sql)
                .setParameter("year", year)
                .setParameter("minx", minx)
                .setParameter("miny", miny)
                .setParameter("maxx", maxx)
                .setParameter("maxy", maxy)
                .getResultList();
    }
}